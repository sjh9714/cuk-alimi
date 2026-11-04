package com.cukalimi.notice;

import com.cukalimi.config.AppProperties;
import com.cukalimi.domain.DateItem;
import com.cukalimi.domain.DateItemRepository;
import com.cukalimi.domain.ImageTranscript;
import com.cukalimi.domain.ImageTranscriptRepository;
import com.cukalimi.domain.Notice;
import com.cukalimi.domain.NoticeRepository;
import com.cukalimi.feed.Audience;
import com.cukalimi.feed.FeedService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 공지 글에서 대상 조건(학년·학과·지역)을 AI가 읽는다 (ADR 013). 공지 한 건에 두 번 읽고, 조건이 같을 때만 저장한다.
 * 어긋나면 저장하지 않고 정규식 결과를 그대로 쓴다. 학생 프로필은 보내지 않는다. 비교는 AudienceFacts가 코드로 한다.
 * 대상은 홈에 나올 수 있는 장학·학사 공지다. 그림 공지는 받아 적은 글이 생긴 뒤에 읽는다.
 */
@Service
public class AudienceReader {

    private static final Logger log = LoggerFactory.getLogger(AudienceReader.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AppProperties props;
    private final NoticeRepository notices;
    private final DateItemRepository dateItems;
    private final ImageTranscriptRepository transcripts;
    private final AudienceClassifier classifier;
    private final Clock clock;

    public AudienceReader(AppProperties props, NoticeRepository notices, DateItemRepository dateItems,
            ImageTranscriptRepository transcripts, AudienceClassifier classifier, Clock clock) {
        this.props = props;
        this.notices = notices;
        this.dateItems = dateItems;
        this.transcripts = transcripts;
        this.classifier = classifier;
        this.clock = clock;
    }

    public record Result(int read, int agreed, boolean limitReached) {
    }

    @Scheduled(cron = "${app.audience-read.cron}", zone = "Asia/Seoul")
    public void readScheduled() {
        try {
            Result r = readPending(LocalDate.now(clock.withZone(SEOUL)));
            if (r.read() > 0) {
                log.info("audience read={} agreed={} limitReached={}", r.read(), r.agreed(), r.limitReached());
            }
        } catch (RuntimeException e) {
            log.error("audience reading failed", e);
        }
    }

    public Result readPending(LocalDate today) {
        if (!classifier.enabled()) {
            return new Result(0, 0, false);
        }
        Map<Long, Notice> targets = new LinkedHashMap<>();
        notices.findByAudienceReadAtIsNullAndPostedOnGreaterThanEqual(today.minusDays(FeedService.UNDATED_DAYS))
                .forEach(n -> targets.put(n.getId(), n));
        // 오래된 공지라도 마감이 남아 홈에 나오면 읽는다.
        List<Long> live = dateItems.findByEndsOnGreaterThanEqualAndStatusNot(today, DateItem.STATUS_CANDIDATE).stream()
                .map(DateItem::getNoticeId).filter(Objects::nonNull).distinct().toList();
        notices.findAllById(live).stream().filter(n -> n.getAudienceReadAt() == null).forEach(n -> targets.putIfAbsent(n.getId(), n));

        OffsetDateTime startOfDay = today.atStartOfDay(SEOUL).toOffsetDateTime();
        int read = 0;
        int agreed = 0;
        for (Notice n : targets.values().stream()
                .sorted(Comparator.comparing(Notice::getPostedOn, Comparator.nullsLast(Comparator.reverseOrder()))).toList()) {
            if (n.isRemoved() || !FeedService.undatedCategory(n) || Audience.RESULT.equals(Audience.of(n).scope()) || waitingForTranscript(n)) {
                continue;
            }
            if (notices.countByAudienceReadAtAfter(startOfDay) >= props.audienceRead().dailyLimit()) {
                return new Result(read, agreed, true);
            }
            try {
                if (read(n)) {
                    agreed++;
                }
                read++;
            } catch (RuntimeException e) {
                log.warn("audience notice {} will be retried: {}", n.getArticleNo(), e.getMessage());
            }
        }
        return new Result(read, agreed, false);
    }

    /** 그림에만 글이 있는 공지는 받아 적기가 끝난 뒤에 읽는다. 받아 적기가 꺼져 있으면 기다리지 않는다. */
    private boolean waitingForTranscript(Notice n) {
        return Notice.REASON_IMAGE_ONLY.equals(n.getReviewReason()) && props.transcribe() != null
                && props.transcribe().enabled() && transcripts.findByArticleNo(n.getArticleNo()).isEmpty();
    }

    /** 두 번 읽어 조건이 같으면 저장하고 true. 다시 해도 소용없는 실패는 비운 채 기록해 다시 부르지 않는다. */
    private boolean read(Notice n) {
        String text = textOf(n);
        AudienceClassifier.Result first;
        AudienceClassifier.Result second;
        try {
            first = classifier.classify(n.getTitle(), text);
        } catch (AudienceClassifier.Failed e) {
            log.warn("audience notice {} failed: {}", n.getArticleNo(), e.getMessage());
            n.recordAudienceRead(null, "failed", e.inputTokens(), e.outputTokens(), OffsetDateTime.now(clock));
            notices.save(n);
            return false;
        }
        try {
            second = classifier.classify(n.getTitle(), text);
        } catch (AudienceClassifier.Failed e) {
            log.warn("audience notice {} second read failed: {}", n.getArticleNo(), e.getMessage());
            n.recordAudienceRead(null, "failed", first.inputTokens() + e.inputTokens(), first.outputTokens() + e.outputTokens(),
                    OffsetDateTime.now(clock));
            notices.save(n);
            return false;
        }
        boolean same = first.facts().sameConditions(second.facts());
        n.recordAudienceRead(same ? first.facts().toJson() : null, same ? first.model() : "disagree:" + first.model(),
                first.inputTokens() + second.inputTokens(), first.outputTokens() + second.outputTokens(), OffsetDateTime.now(clock));
        notices.save(n);
        return same;
    }

    /** 본문 글에 받아 적은 그림 글을 붙인다. 두 번째 받아 적기(#second)는 같은 그림이라 넣지 않는다. */
    private String textOf(Notice n) {
        String images = transcripts.findByArticleNo(n.getArticleNo()).stream()
                .filter(t -> !t.getImageUrl().endsWith(ImageNoticeReader.SECOND_READ))
                .map(ImageTranscript::getText).filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining("\n"));
        String body = n.getBodyText() == null ? "" : n.getBodyText();
        return images.isBlank() ? body : body + "\n\n그림에서 받아 적은 글:\n" + images;
    }
}
