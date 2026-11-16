package com.cukalimi.notice;

import com.cukalimi.config.AppProperties;
import com.cukalimi.domain.DateItem;
import com.cukalimi.domain.DateItemRepository;
import com.cukalimi.domain.LocatorRead;
import com.cukalimi.domain.LocatorReadRepository;
import com.cukalimi.domain.Notice;
import com.cukalimi.domain.NoticeRawRepository;
import com.cukalimi.domain.NoticeRepository;
import com.cukalimi.feed.Audience;
import com.cukalimi.feed.FeedService;
import com.cukalimi.ops.AiBudget;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 규칙이 놓친 마감 문장을 AI가 찾는다 (ADR 016). 작은 모델이 공지를 두 번 읽고, 그 답이 화면을 바꾸려 할 때만 큰 모델이 한 번 읽어 확인한다.
 * AI는 줄을 옮길 뿐이다. 그 줄이 원문에 있는지 확인하고 날짜를 읽는 일은 QuoteCheck가, 규칙의 결과와 합치는 일은 LocatorMerge가 한다.
 * 대상은 홈에 나오는 장학·학사·학과 공지의 글이다. 날짜가 그림에만 있는 공지는 받아 적기(ImageNoticeReader)가 맡는다.
 */
@Service
public class LocatorReader {

    private static final Logger log = LoggerFactory.getLogger(LocatorReader.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AppProperties props;
    private final NoticeRepository notices;
    private final NoticeRawRepository raws;
    private final DateItemRepository dateItems;
    private final LocatorReadRepository reads;
    private final DeadlineLocators locators;
    private final NoticeStore store;
    private final AiBudget budget;
    private final Clock clock;

    public LocatorReader(AppProperties props, NoticeRepository notices, NoticeRawRepository raws, DateItemRepository dateItems,
            LocatorReadRepository reads, DeadlineLocators locators, NoticeStore store, AiBudget budget, Clock clock) {
        this.props = props;
        this.notices = notices;
        this.raws = raws;
        this.dateItems = dateItems;
        this.reads = reads;
        this.locators = locators;
        this.store = store;
        this.budget = budget;
        this.clock = clock;
    }

    /** published는 새로 공개한 날짜, doubted는 "확인"을 단 규칙 날짜 수다. */
    public record Result(int read, int published, int doubted, boolean limitReached) {
    }

    @Scheduled(cron = "${app.locator.cron}", zone = "Asia/Seoul")
    public void readScheduled() {
        try {
            Result r = readPending(LocalDate.now(clock.withZone(SEOUL)));
            if (r.read() > 0) {
                log.info("locator read={} published={} doubted={} limitReached={}", r.read(), r.published(), r.doubted(), r.limitReached());
            }
        } catch (RuntimeException e) {
            log.error("locator reading failed", e);
        }
    }

    public Result readPending(LocalDate today) {
        AppProperties.Locator conf = props.locator();
        if (conf == null || !conf.enabled() || !locators.small().enabled()) {
            return new Result(0, 0, 0, false);
        }
        // 이번 달 AI 비용이 상한에 닿았으면 읽지 않는다. 아침 운영자 메일이 알린다.
        if (!budget.allow()) {
            return new Result(0, 0, 0, true);
        }
        Map<Long, Notice> targets = new LinkedHashMap<>();
        notices.findByLocatorReadAtIsNullAndPostedOnGreaterThanEqual(today.minusDays(FeedService.UNDATED_DAYS))
                .forEach(n -> targets.put(n.getId(), n));
        // 오래된 공지라도 마감이 남아 홈에 나오면 읽는다.
        List<Long> live = dateItems.findByEndsOnGreaterThanEqualAndStatusNotIn(today, DateItem.HIDDEN).stream()
                .map(DateItem::getNoticeId).filter(Objects::nonNull).distinct().toList();
        notices.findAllById(live).stream().filter(n -> n.getLocatorReadAt() == null).forEach(n -> targets.putIfAbsent(n.getId(), n));

        OffsetDateTime startOfDay = today.atStartOfDay(SEOUL).toOffsetDateTime();
        int read = 0;
        int published = 0;
        int doubted = 0;
        for (Notice n : targets.values().stream()
                .sorted(Comparator.comparing(Notice::getPostedOn, Comparator.nullsLast(Comparator.reverseOrder()))).toList()) {
            if (!wanted(n)) {
                continue;
            }
            if (reads.countNoticesSince(startOfDay) >= conf.dailyLimit() || !budget.allow()) {
                return new Result(read, published, doubted, true);
            }
            try {
                NoticeStore.Located saved = read(n, conf, startOfDay);
                read++;
                published += saved.published();
                doubted += saved.doubted();
            } catch (LargeLimitReached e) {
                return new Result(read, published, doubted, true);
            } catch (RuntimeException e) {
                log.warn("locator notice {} will be retried: {}", n.getArticleNo(), e.getMessage());
            }
        }
        return new Result(read, published, doubted, false);
    }

    /** 큰 모델의 하루 상한에 닿아 확인받지 못했다. */
    private static final class LargeLimitReached extends RuntimeException {
        LargeLimitReached() {
            super("large model daily limit reached", null, false, false);
        }
    }

    /** 홈에 나올 수 있는 글 공지인가. 취업 공지는 기업 포스터가 많아 뺀다. 결과 발표는 할 일이 없다. */
    private static boolean wanted(Notice n) {
        if (n.isRemoved() || !(FeedService.undatedCategory(n) || n.getDept() != null)) {
            return false;
        }
        if (Notice.REASON_IMAGE_ONLY.equals(n.getReviewReason()) || Notice.REASON_PARSE_FAILED.equals(n.getReviewReason())) {
            return false;
        }
        return !Audience.RESULT.equals(Audience.of(n).scope());
    }

    private NoticeStore.Located read(Notice n, AppProperties.Locator conf, OffsetDateTime startOfDay) {
        String html = raws.findById(n.getArticleNo()).map(r -> r.getHtml()).orElse("");
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (html == null || html.isBlank()) {
            n.markLocatorRead(now);
            notices.save(n);
            return new NoticeStore.Located(0, 0, 0);
        }
        NoticeView view = NoticeViewParser.parse(html);
        List<ExtractedItem> rule = DateItemExtractor.extract(view);
        NoticeText text = NoticeText.of(view);
        LocalDate postedOn = view.postedOn() == null ? n.getPostedOn() : view.postedOn();

        List<List<QuoteCheck.Located>> readings = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            LocatorReading reading = call(locators.small(), n, text, postedOn, now);
            if (reading == null) {
                // 다시 해도 소용없는 실패다. 읽은 것으로 적어 다시 부르지 않고, 규칙의 결과는 그대로 둔다.
                n.markLocatorRead(now);
                notices.save(n);
                return new NoticeStore.Located(0, 0, 0);
            }
            readings.add(QuoteCheck.check(text, postedOn, reading));
        }
        // 작은 모델의 답이 화면을 바꾸려 할 때만 큰 모델에게 확인받는다. 큰 모델이 꺼져 있으면 아무것도 공개하지 않고 보류한다.
        DeadlineLocator large = locators.large();
        List<QuoteCheck.Located> confirmed = null;
        if (LocatorMerge.needsReview(rule, readings) && large.enabled()) {
            // 큰 모델의 하루 상한에 닿았으면 이 공지는 읽지 않은 것으로 두고 오늘은 멈춘다. 보류로 굳히면 내일도 확인받지 못한다.
            if (reads.countByModelAndCreatedAtGreaterThanEqual(large.model(), startOfDay) >= conf.adjudicatorDailyLimit()) {
                throw new LargeLimitReached();
            }
            LocatorReading third = call(large, n, text, postedOn, now);
            if (third != null) {
                confirmed = QuoteCheck.check(text, postedOn, third);
            }
        }
        NoticeStore.Located saved = store.saveLocatedItems(n.getId(), n.getArticleNo(), LocatorMerge.merge(view, rule, readings, confirmed));
        Notice fresh = notices.findById(n.getId()).orElseThrow();
        fresh.markLocatorRead(now);
        notices.save(fresh);
        return saved;
    }

    /** 한 번 부르고 기록한다. 다시 해도 소용없는 실패면 null. 그 밖의 예외는 던져 다음 차례에 다시 한다. */
    private LocatorReading call(DeadlineLocator locator, Notice n, NoticeText text, LocalDate postedOn, OffsetDateTime now) {
        try {
            DeadlineLocator.Result r = locator.locate(text, postedOn);
            reads.save(new LocatorRead(n.getId(), r.model(), ClaudeDeadlineLocator.promptHash(), LocatorRead.STATUS_READ, json(r.reading()),
                    r.inputTokens(), r.outputTokens(), now));
            return r.reading();
        } catch (DeadlineLocator.Failed e) {
            log.warn("locator notice {} failed: {}", n.getArticleNo(), e.getMessage());
            reads.save(new LocatorRead(n.getId(), locator.model(), ClaudeDeadlineLocator.promptHash(), LocatorRead.STATUS_FAILED, null,
                    e.inputTokens(), e.outputTokens(), now));
            return null;
        }
    }

    private static String json(LocatorReading reading) {
        try {
            return JSON.writeValueAsString(reading);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
