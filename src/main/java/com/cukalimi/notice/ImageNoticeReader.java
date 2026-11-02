package com.cukalimi.notice;

import com.cukalimi.config.AppProperties;
import com.cukalimi.domain.DateItem;
import com.cukalimi.domain.DateItemRepository;
import com.cukalimi.domain.ImageTranscript;
import com.cukalimi.domain.ImageTranscriptRepository;
import com.cukalimi.domain.Notice;
import com.cukalimi.domain.NoticeRawRepository;
import com.cukalimi.domain.NoticeRepository;
import com.cukalimi.domain.ReviewEvent;
import com.cukalimi.domain.ReviewEventRepository;
import com.cukalimi.feed.FeedService;
import com.cukalimi.feed.ItemKind;
import com.cukalimi.fetch.JsoupPageFetcher;
import com.cukalimi.fetch.PageFetcher;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 날짜가 그림에만 있는 공지의 그림을 AI가 받아 적고, 그 글을 공지 날짜 규칙에 그대로 넣는다 (ADR 010).
 * AI는 글자만 옮긴다. 날짜는 규칙이 뽑고, 뽑은 날짜는 후보로 저장해 운영자가 승인해야 학생에게 나간다.
 * 대상은 홈의 "날짜 확인 중"에 있는 공지와 같다. 그림 주소마다 한 번만 받아 적고, 하루 상한을 넘기지 않는다.
 */
@Service
public class ImageNoticeReader {

    private static final Logger log = LoggerFactory.getLogger(ImageNoticeReader.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 공지 하나에서 받아 적는 그림 수의 상한. 그림이 수십 장인 공지(강의 변경표 묶음)가 하루치를 다 쓰지 않게. */
    static final int MAX_IMAGES_PER_NOTICE = 6;
    /** 두 번째 받아 적기는 그림 주소에 이 꼬리를 붙여 같은 표에 둔다. 비용과 하루 상한이 함께 세어진다. */
    static final String SECOND_READ = "#second";

    private final AppProperties props;
    private final NoticeRepository notices;
    private final NoticeRawRepository raws;
    private final DateItemRepository dateItems;
    private final ImageTranscriptRepository transcripts;
    private final PageFetcher fetcher;
    private final ImageTranscriber transcriber;
    private final NoticeStore store;
    private final ReviewEventRepository reviewEvents;
    private final Clock clock;

    public ImageNoticeReader(AppProperties props, NoticeRepository notices, NoticeRawRepository raws,
            DateItemRepository dateItems, ImageTranscriptRepository transcripts, PageFetcher fetcher,
            ImageTranscriber transcriber, NoticeStore store, ReviewEventRepository reviewEvents, Clock clock) {
        this.props = props;
        this.notices = notices;
        this.raws = raws;
        this.dateItems = dateItems;
        this.transcripts = transcripts;
        this.fetcher = fetcher;
        this.transcriber = transcriber;
        this.store = store;
        this.reviewEvents = reviewEvents;
        this.clock = clock;
    }

    public record Result(int notices, int newTranscripts, int candidates, boolean limitReached) {
    }

    @Scheduled(cron = "${app.transcribe.cron}", zone = "Asia/Seoul")
    public void readScheduled() {
        try {
            Result r = readPending(LocalDate.now(clock.withZone(SEOUL)));
            if (r.notices() > 0) {
                log.info("image notices={} newTranscripts={} candidates={} limitReached={}", r.notices(),
                        r.newTranscripts(), r.candidates(), r.limitReached());
            }
        } catch (RuntimeException e) {
            log.error("image reading failed", e);
        }
    }

    public Result readPending(LocalDate today) {
        if (!transcriber.enabled()) {
            return new Result(0, 0, 0, false);
        }
        int seen = 0;
        int fresh = 0;
        int candidates = 0;
        Map<Long, Notice> targets = new LinkedHashMap<>();
        notices.findByStatusAndReviewReasonAndPostedOnGreaterThanEqual(Notice.STATUS_NEEDS_REVIEW, Notice.REASON_IMAGE_ONLY,
                today.minusDays(FeedService.UNDATED_DAYS)).forEach(n -> targets.put(n.getId(), n));
        // 30일이 지나도 후보 날짜가 남은 공지는 계속 본다. 조용히 사라지면 마감 전 공지를 놓친다.
        notices.findAllById(dateItems.findNoticeIdsWithOpenItems(DateItem.STATUS_CANDIDATE, today))
                .forEach(n -> targets.putIfAbsent(n.getId(), n));
        for (Notice n : targets.values()) {
            if (!FeedService.undatedCategory(n) || !waitingForImageRead(n)) {
                continue;
            }
            seen++;
            try {
                Read read = read(n, today);
                fresh += read.newTranscripts();
                candidates += read.candidates();
                if (read.limitReached()) {
                    return new Result(seen, fresh, candidates, true);
                }
            } catch (RuntimeException e) {
                log.warn("image notice {} failed: {}", n.getArticleNo(), e.getMessage());
            }
        }
        return new Result(seen, fresh, candidates, false);
    }

    private record Read(int newTranscripts, int candidates, boolean limitReached) {
    }

    /** 항목이 없거나 후보만 있는 공지만 본다. 신고로 내렸거나 운영자가 지운 날짜가 있으면 다시 올리지 않고 사람에게 맡긴다. */
    private boolean waitingForImageRead(Notice n) {
        return !reviewEvents.existsByNoticeIdAndAction(n.getId(), ReviewEvent.DELETE)
                && dateItems.findByNoticeIdOrderByEndsOnAsc(n.getId()).stream()
                        .allMatch(i -> DateItem.STATUS_CANDIDATE.equals(i.getStatus()) && !i.isWithdrawn());
    }

    private Read read(Notice n, LocalDate today) {
        NoticeView view = NoticeViewParser.parse(raws.findById(n.getArticleNo()).orElseThrow().getHtml());
        List<String> texts = new ArrayList<>();
        List<String> secondTexts = new ArrayList<>();
        int fresh = 0;
        // 이번에 읽지 못한 그림(학교 서버나 API의 일시 오류)이 있으면 후보를 저장하지 않고 다음 차례에 다시 본다.
        // 그 사이 받아 적은 그림은 캐시에 남으니 다시 돈이 들지 않는다.
        boolean pending = false;
        for (String url : imageUrls(view)) {
            for (String key : List.of(url, url + SECOND_READ)) {
                List<String> into = key.equals(url) ? texts : secondTexts;
                Optional<ImageTranscript> cached = transcripts.findById(key);
                if (cached.isPresent()) {
                    into.add(cached.get().getText());
                    continue;
                }
                String skip = skipReason(url);
                if (skip == null && transcripts.countByCreatedAtAfterAndInputTokensGreaterThan(
                        today.atStartOfDay(SEOUL).toOffsetDateTime(), 0L) >= props.transcribe().dailyLimit()) {
                    return new Read(fresh, 0, true);
                }
                ImageTranscriber.Result r;
                try {
                    r = skip != null ? new ImageTranscriber.Result("", skip, 0, 0) : transcribe(url);
                } catch (RuntimeException e) {
                    log.warn("image {} will be retried: {}", url, e.getMessage());
                    pending = true;
                    continue;
                }
                transcripts.save(new ImageTranscript(key, n.getArticleNo(), r.text(), r.model(), r.inputTokens(),
                        r.outputTokens()));
                into.add(r.text());
                fresh += skip == null ? 1 : 0;
            }
        }
        if (pending) {
            return new Read(fresh, 0, false);
        }
        List<ExtractedItem> first = extractFromTranscripts(view, texts);
        List<ExtractedItem> second = extractFromTranscripts(view, secondTexts);
        return new Read(fresh, store.saveImageItems(n.getId(), view.articleNo(), first,
                agreedDates(first, second, view.postedOn(), view.cleanTitle())), false);
    }

    /**
     * 공개해도 되는 날짜. 두 번 읽은 할 일 날짜가 모두 같고 게시일부터 1년 안이어야 한다. 하나라도 어긋나면 비어 있다.
     * 할 일이 있는 공지에서는 두 번 모두 나온 안내 날짜도 함께 공개한다. 할 일이 없으면 행사 날짜를 같은 기준으로 본다.
     * 2026-11-02 시험은 docs/verification/2026-11-02-dual-read.md. 행사 날짜는 이 시험에 들어 있지 않다.
     */
    static Set<LocalDate> agreedDates(List<ExtractedItem> first, List<ExtractedItem> second, LocalDate postedOn, String noticeTitle) {
        Set<LocalDate> actions = actionDates(first);
        if (actions.isEmpty() && actionDates(second).isEmpty()) {
            Set<LocalDate> events = eventDates(first, noticeTitle);
            return !events.isEmpty() && events.equals(eventDates(second, noticeTitle)) && inWindow(events, postedOn) ? events : Set.of();
        }
        if (actions.isEmpty() || !actions.equals(actionDates(second)) || !inWindow(actions, postedOn)) {
            return Set.of();
        }
        Set<LocalDate> agreed = new TreeSet<>(actions);
        Set<LocalDate> inSecond = second.stream().map(i -> i.range().end()).collect(Collectors.toSet());
        first.stream().map(i -> i.range().end()).filter(inSecond::contains).forEach(agreed::add);
        return agreed;
    }

    private static boolean inWindow(Set<LocalDate> dates, LocalDate postedOn) {
        return dates.stream().noneMatch(d -> d.isBefore(postedOn) || d.isAfter(postedOn.plusYears(1)));
    }

    private static Set<LocalDate> eventDates(List<ExtractedItem> items, String noticeTitle) {
        return items.stream()
                .filter(i -> ItemKind.isEvent(i.title(), noticeTitle, i.range().start() == null || i.range().isSingleDay()))
                .map(i -> i.range().end()).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<LocalDate> actionDates(List<ExtractedItem> items) {
        return items.stream().filter(i -> ItemKind.ACTION.equals(ItemKind.of(i.title()))).map(i -> i.range().end())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** 본문 그림 주소. 학교 주소의 http는 https로 맞춘다(학교가 https로 넘기고, 넘기는 응답은 따라가지 않는다). 깨진 주소는 뺀다. */
    private List<String> imageUrls(NoticeView view) {
        URI base = URI.create(props.baseUrl() + "/");
        List<String> urls = new ArrayList<>();
        for (String src : view.body().select("img").eachAttr("src")) {
            if (src.isBlank() || src.startsWith("data:")) {
                continue;
            }
            try {
                URI u = base.resolve(src.trim());
                if ("http".equals(u.getScheme()) && base.getHost().equalsIgnoreCase(u.getHost())) {
                    u = new URI(base.getScheme(), null, u.getHost(), base.getPort(), u.getPath(), u.getQuery(), null);
                }
                urls.add(u.toString());
            } catch (IllegalArgumentException | java.net.URISyntaxException e) {
                log.warn("bad image src in notice {}: {}", view.articleNo(), src);
            }
        }
        return urls.stream().distinct().limit(MAX_IMAGES_PER_NOTICE).toList();
    }

    /** 받아 적은 글을 공지 본문 자리에 넣고 글 공지와 같은 규칙으로 날짜를 뽑는다. 품질 백테스트도 이 메서드를 쓴다. */
    public static List<ExtractedItem> extractFromTranscripts(NoticeView view, List<String> texts) {
        String html = String.join("\n", texts).replace("```html", "").replace("```", "");
        NoticeView transcribed = new NoticeView(view.articleNo(), view.category(), view.title(), view.postedOn(),
                view.writer(), Jsoup.parseBodyFragment(html).body(), html, false, List.of());
        return DateItemExtractor.extract(transcribed);
    }

    /** 받아 보기 전에 건너뛸 그림. 학교 밖 주소와 받지 않는 형식이다. */
    private String skipReason(String url) {
        if (!onSchoolSite(url, props.baseUrl())) {
            return "skipped:host";
        }
        return mediaType(url) == null ? "skipped:type" : null;
    }

    /**
     * 그림을 받아 받아 적는다. 다시 해도 소용없는 경우는 빈 글로 기록해 다시 시도하지 않는다.
     * 학교 서버의 3xx·4xx, 너무 큰 그림, API가 받지 않는 그림, 그리고 요금이 나간 실패(끝까지 적지 못함)다.
     * 요금이 나간 실패는 토큰 수를 남겨 비용과 하루 상한에 센다.
     * 학교 서버의 5xx·시간 초과, API의 과부하·요청 한도처럼 일시적인 오류는 예외를 그대로 던진다. 요금이 없으니 다음 차례에 다시 한다.
     */
    private ImageTranscriber.Result transcribe(String url) {
        byte[] bytes;
        try {
            bytes = fetcher.getBytes(url);
        } catch (java.io.UncheckedIOException e) {
            if (e.getCause() instanceof org.jsoup.HttpStatusException h && h.getStatusCode() >= 300 && h.getStatusCode() < 500) {
                return new ImageTranscriber.Result("", "skipped:fetch-" + h.getStatusCode(), 0, 0);
            }
            throw e;
        }
        if (bytes.length > JsoupPageFetcher.MAX_IMAGE_BYTES) {
            return new ImageTranscriber.Result("", "skipped:size", 0, 0);
        }
        try {
            return transcriber.transcribe(bytes, mediaType(url));
        } catch (ImageTranscriber.Rejected e) {
            log.warn("transcribe {} rejected: {}", url, e.getMessage());
            return new ImageTranscriber.Result("", "skipped:rejected", 0, 0);
        } catch (ImageTranscriber.ChargedFailure e) {
            log.warn("transcribe {} failed after charge: {}", url, e.getMessage());
            return new ImageTranscriber.Result("", "failed", e.inputTokens(), e.outputTokens());
        }
    }

    /** 공지 본문의 그림 주소는 학교가 아닌 곳을 가리킬 수 있다. 서버가 아무 주소나 부르지 않게 학교 주소만 받는다. */
    static boolean onSchoolSite(String url, String baseUrl) {
        URI u = URI.create(url);
        URI base = URI.create(baseUrl);
        return ("https".equals(u.getScheme()) || "http".equals(u.getScheme()))
                && base.getHost().equalsIgnoreCase(u.getHost()) && u.getPort() == base.getPort() && u.getUserInfo() == null;
    }

    static String mediaType(String url) {
        String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".png")) {
            return "image/png";
        }
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (path.endsWith(".gif")) {
            return "image/gif";
        }
        if (path.endsWith(".webp")) {
            return "image/webp";
        }
        return null;
    }
}
