package com.cukalimi.notice;

import com.cukalimi.config.AppProperties;
import com.cukalimi.domain.DateItem;
import com.cukalimi.domain.DateItemRepository;
import com.cukalimi.domain.Notice;
import com.cukalimi.domain.NoticeRepository;
import com.cukalimi.fetch.PageFetcher;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 공지 목록을 1시간마다 읽고, 처음 보는 글과 제목이 바뀐 글만 본문을 가져와 저장한다.
 * 글 하나가 실패해도 나머지는 계속 간다. 실패한 글은 PARSE_FAILED로 검수 큐에 들어간다.
 * 모든 공지는 NEEDS_REVIEW로 들어온다. 검수는 사람이 한다.
 */
@Service
public class NoticeCollector {

    private static final Logger log = LoggerFactory.getLogger(NoticeCollector.class);

    private final AppProperties props;
    private final PageFetcher fetcher;
    private final NoticeRepository notices;
    private final NoticeStore store;
    private final DateItemRepository dateItems;
    private final Clock clock;

    public NoticeCollector(AppProperties props, PageFetcher fetcher, NoticeRepository notices, NoticeStore store,
            DateItemRepository dateItems, Clock clock) {
        this.dateItems = dateItems;
        this.clock = clock;
        this.props = props;
        this.fetcher = fetcher;
        this.notices = notices;
        this.store = store;
    }

    public record Result(int listed, int fetched, int items, int needsManual, int failed) {
        Result plus(Result o) {
            return new Result(listed + o.listed, fetched + o.fetched, items + o.items, needsManual + o.needsManual,
                    failed + o.failed);
        }
    }

    @Scheduled(cron = "${app.notice.cron}", zone = "Asia/Seoul")
    public void collectScheduled() {
        try {
            Result r = collectAll();
            log.info("notice listed={} fetched={} items={} needsManual={} failed={}", r.listed(), r.fetched(), r.items(),
                    r.needsManual(), r.failed());
        } catch (RuntimeException e) {
            log.error("notice collect failed", e);
        }
    }

    public Result collectAll() {
        Result total = new Result(0, 0, 0, 0, 0);
        for (AppProperties.Category category : props.notice().categories()) {
            try {
                total = total.plus(collectCategory(category.id()));
            } catch (RuntimeException e) {
                log.error("notice category {} list failed", category.name(), e);
            }
        }
        return total;
    }

    public Result collectCategory(int categoryId) {
        String listHtml = fetcher.get(props.noticeListUrl(categoryId));
        List<NoticeSummary> summaries = NoticeListParser.parse(listHtml);
        int fetched = 0;
        int items = 0;
        int needsManual = 0;
        int failed = 0;
        for (NoticeSummary summary : summaries) {
            // 이미 본 글은 목록 제목이 바뀌었을 때만 다시 읽는다. "(기간 연장)"처럼 학교가 고친 글이다.
            var known = notices.findByArticleNo(summary.articleNo());
            if (known.isPresent() && !titleChanged(known.get().getTitle(), summary.title())) {
                continue;
            }
            String html = null;
            try {
                html = fetcher.get(props.noticeViewUrl(summary.articleNo()));
                NoticeStore.Saved saved = store.save(summary, html);
                fetched++;
                items += saved.items();
                if (saved.items() == 0) {
                    needsManual++;
                }
            } catch (RuntimeException e) {
                failed++;
                try {
                    store.saveFailure(summary, html, e.getMessage());
                } catch (RuntimeException inner) {
                    log.error("notice {} could not even record the failure", summary.articleNo(), inner);
                }
            }
        }
        return new Result(summaries.size(), fetched, items, needsManual, failed);
    }

    /**
     * 아직 지나지 않은 날짜가 있는 공지를 하루 한 번 다시 읽는다. 학교는 제목을 그대로 두고 본문의 기간만 고치기도 한다.
     * 본문이 바뀐 공지만 다시 처리한다. 고친 수.
     */
    public int recheckOpenNotices(LocalDate today) {
        Set<Long> ids = dateItems.findByEndsOnGreaterThanEqual(today).stream().map(DateItem::getNoticeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        int changed = 0;
        for (Notice n : notices.findAllById(ids)) {
            try {
                String html = fetcher.get(n.getUrl());
                if (!normalize(NoticeViewParser.parse(html).bodyText()).equals(normalize(n.getBodyText()))) {
                    store.save(new NoticeSummary(n.getArticleNo(), n.getCategory(), n.getTitle(), n.getPostedOn()), html);
                    changed++;
                }
            } catch (RuntimeException e) {
                log.warn("notice {} recheck failed: {}", n.getArticleNo(), e.getMessage());
            }
        }
        return changed;
    }

    @Scheduled(cron = "${app.notice.recheck-cron}", zone = "Asia/Seoul")
    public void recheckScheduled() {
        try {
            log.info("notice recheck changed={}", recheckOpenNotices(LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")))));
        } catch (RuntimeException e) {
            log.error("notice recheck failed", e);
        }
    }

    /** 공백과 보이지 않는 글자는 무시한다. 목록과 본문 페이지가 제목을 조금 다르게 찍어도 매시간 다시 읽지 않게. */
    static boolean titleChanged(String stored, String listed) {
        return listed != null && !listed.isBlank() && !normalize(stored).equals(normalize(listed));
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\uFEFF\\u200B-\\u200D]", "");
    }
}
