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
        for (AppProperties.Board board : props.notice().boardsOrEmpty()) {
            try {
                total = total.plus(collectBoard(board));
            } catch (RuntimeException e) {
                log.error("notice board {} list failed", board.name(), e);
            }
        }
        return total;
    }

    public Result collectCategory(int categoryId) {
        return collect(NoticeListParser.parse(fetcher.get(props.noticeListUrl(categoryId))));
    }

    /** 학과 게시판. 목록의 분류 칸이 없으니 분류는 "학과"로 두고 학과 이름과 글 주소를 붙인다 (ADR 002 추가). */
    public Result collectBoard(AppProperties.Board board) {
        List<NoticeSummary> summaries = NoticeListParser.parse(fetcher.get(board.listUrl())).stream()
                .map(s -> new NoticeSummary(s.articleNo(), CATEGORY_DEPT, s.title(), s.postedOn(), board.viewUrl(s.articleNo()), board.dept()))
                .toList();
        return collect(summaries);
    }

    public static final String CATEGORY_DEPT = "학과";

    private Result collect(List<NoticeSummary> summaries) {
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
                html = fetcher.get(summary.url() == null ? props.noticeViewUrl(summary.articleNo()) : summary.url());
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
            if (n.isRemoved()) {
                continue;
            }
            try {
                String html = fetcher.get(n.getUrl());
                if (!normalize(NoticeViewParser.parse(html).bodyText()).equals(normalize(n.getBodyText()))) {
                    store.save(new NoticeSummary(n.getArticleNo(), n.getCategory(), n.getTitle(), n.getPostedOn(), n.getUrl(), n.getDept()), html);
                    changed++;
                }
            } catch (RuntimeException e) {
                // 학교가 지운 글은 404다. 학생 화면과 알림에서 뺀다. 학교가 같은 공지를 고쳐 다시 올릴 때 흔하다.
                if (isNotFound(e)) {
                    store.retire(n.getId(), Notice.REMOVED_GONE);
                    changed++;
                    log.info("notice {} gone (404), retired", n.getArticleNo());
                } else {
                    log.warn("notice {} recheck failed: {}", n.getArticleNo(), e.getMessage());
                }
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

    static boolean isNotFound(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof org.jsoup.HttpStatusException h && h.getStatusCode() == 404) {
                return true;
            }
        }
        return false;
    }

    /** 공백과 보이지 않는 글자는 무시한다. 목록과 본문 페이지가 제목을 조금 다르게 찍어도 매시간 다시 읽지 않게. */
    static boolean titleChanged(String stored, String listed) {
        return listed != null && !listed.isBlank() && !normalize(stored).equals(normalize(listed));
    }

    static String normalize(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\uFEFF\\u200B-\\u200D]", "");
    }
}
