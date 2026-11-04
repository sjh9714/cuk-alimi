package com.cukalimi.notice;

import com.cukalimi.config.AppProperties;
import com.cukalimi.domain.DateItem;
import com.cukalimi.domain.DateItemRepository;
import com.cukalimi.domain.Notice;
import com.cukalimi.domain.NoticeRaw;
import com.cukalimi.domain.NoticeRawRepository;
import com.cukalimi.domain.NoticeRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 한 건을 원문·공지·날짜 항목으로 저장한다. 세 가지가 한 트랜잭션에 들어간다.
 * 수집기와 다른 빈이어야 @Transactional 프록시가 실제로 걸린다.
 */
@Service
public class NoticeStore {

    private static final Logger log = LoggerFactory.getLogger(NoticeStore.class);

    private final AppProperties props;
    private final NoticeRepository notices;
    private final NoticeRawRepository raws;
    private final DateItemRepository dateItems;

    public NoticeStore(AppProperties props, NoticeRepository notices, NoticeRawRepository raws,
            DateItemRepository dateItems) {
        this.props = props;
        this.notices = notices;
        this.raws = raws;
        this.dateItems = dateItems;
    }

    public record Saved(long noticeId, int items, String reason) {
    }

    /** 지워졌거나 묵은 공지로 표시한다. 날짜 항목은 그대로 두고 화면과 알림에서만 뺀다. */
    @Transactional
    public void retire(long noticeId, String reason) {
        notices.findById(noticeId).ifPresent(n -> n.markRemoved(reason));
    }

    /**
     * 학교가 같은 공지를 고쳐 다시 올리면 같은 날 같은 제목의 글이 둘이 된다. 지운 쪽을 알아채는 재확인은 하루 한 번이라,
     * 새 글이 들어올 때 같은 분류·날짜·제목의 번호 작은 글을 바로 묵힌다.
     */
    private void retireDuplicates(Notice fresh) {
        for (Notice other : notices.findByCategoryAndPostedOn(fresh.getCategory(), fresh.getPostedOn())) {
            if (other.getArticleNo() < fresh.getArticleNo() && !other.isRemoved()
                    && NoticeCollector.normalize(other.getTitle()).equals(NoticeCollector.normalize(fresh.getTitle()))) {
                other.markRemoved(Notice.REMOVED_DUPLICATE);
                log.info("notice {} retired as duplicate of {}", other.getArticleNo(), fresh.getArticleNo());
            }
        }
    }

    /** 본문을 읽고 저장한다. 이미 있는 글이면 원문과 항목을 다시 처리한다(검수 완료 항목은 건드리지 않는다). */
    @Transactional
    public Saved save(NoticeSummary summary, String html) {
        NoticeView view = NoticeViewParser.parse(html);
        List<ExtractedItem> extracted = DateItemExtractor.extract(view);
        // 날짜를 못 읽었는데 그림이 있으면 날짜가 그림 안에 있을 가능성이 크다. 운영자가 그림을 열어 보게 한다.
        String reason = extracted.isEmpty()
                ? (view.hasImages() ? Notice.REASON_IMAGE_ONLY : Notice.REASON_NO_DATE)
                : Notice.REASON_PARSED;
        // 상단 고정 글은 본문 페이지의 분류 자리에 "공지"가 찍히므로 목록에서 읽은 분류를 쓴다.
        String category = summary.category() == null || summary.category().isBlank() ? view.category() : summary.category();

        raws.save(new NoticeRaw(view.articleNo(), html));
        Notice notice = notices.findByArticleNo(view.articleNo()).orElse(null);
        if (notice == null) {
            String url = summary.url() == null ? props.noticeViewUrl(view.articleNo()) : summary.url();
            notice = notices.save(new Notice(view.articleNo(), category, summary.dept(), view.title(), view.postedOn(),
                    view.writer(), url, view.bodyText(), view.hasImages(),
                    String.join("\n", view.attachments()), reason));
            retireDuplicates(notice);
        } else {
            boolean changed = notice.reparsed(view.title(), view.bodyText(), view.hasImages(),
                    String.join("\n", view.attachments()), reason);
            // 학교가 공지를 고쳤으면(기간 연장 등) 옛 그림에서 읽은 후보를 지운다. 받아 적기가 새 그림으로 다시 읽는다.
            // 공개했던 날짜는 지우지 않고 거두어 둔다. 지우면 학생의 저장과 마감 알림이 함께 사라진다.
            if (changed) {
                for (DateItem i : dateItems.findByNoticeIdOrderByEndsOnAsc(notice.getId())) {
                    // 신고로 내린 날짜는 남긴다. 지우면 사람이 보기 전에 다시 자동 공개된다.
                    if (DateItem.STATUS_CANDIDATE.equals(i.getStatus()) && !i.isWithdrawn()) {
                        dateItems.delete(i);
                    } else {
                        i.awaitReread();
                    }
                }
            }
        }

        Set<String> seen = new HashSet<>();
        for (ExtractedItem item : extracted) {
            DateItem fresh = DateItem.fromNotice(notice.getId(), item.naturalKey(view.articleNo()), item.title(),
                    item.range().start(), item.range().end(), item.range().startsAt(), item.range().endsAt(),
                    item.targetGrade());
            seen.add(fresh.getNaturalKey());
            var existing = dateItems.findByNaturalKey(fresh.getNaturalKey());
            if (existing.isEmpty()) {
                dateItems.save(fresh);
            } else {
                existing.get().refreshFrom(fresh);
            }
        }
        // 다시 처리했더니 사라진 자동 항목은 지운다. 사람이 고친 항목은 남긴다.
        for (DateItem old : dateItems.findByNoticeIdOrderByEndsOnAsc(notice.getId())) {
            if (!seen.contains(old.getNaturalKey()) && DateItem.STATUS_AUTO.equals(old.getStatus())
                    && DateItem.SOURCE_NOTICE.equals(old.getSourceKind())) {
                dateItems.delete(old);
            }
        }
        log.info("notice {} [{}] {} -> {} items ({})", view.articleNo(), category, view.title(), extracted.size(), reason);
        return new Saved(notice.getId(), extracted.size(), reason);
    }

    /**
     * 그림을 받아 적은 글에서 읽은 날짜를 후보로 넣는다. 같은 자리의 항목이 이미 있으면 내용은 건드리지 않는다.
     * agreedDates에 든 날짜는 두 번 읽기가 일치한 것이라 바로 공개한다. 새로 넣은 수.
     */
    @Transactional
    public int saveImageItems(long noticeId, long articleNo, List<ExtractedItem> extracted, Set<LocalDate> agreedDates) {
        int added = 0;
        Set<String> seen = new HashSet<>();
        for (ExtractedItem item : extracted) {
            String key = "notice:" + articleNo + ":img." + item.where();
            seen.add(key);
            DateItem row = dateItems.findByNaturalKey(key).orElse(null);
            if (row == null) {
                row = dateItems.save(DateItem.fromImage(noticeId, key, item.title(), item.range().start(), item.range().end(),
                        item.range().startsAt(), item.range().endsAt(), item.targetGrade()));
                added++;
            } else {
                row.refreshCandidate(item.title(), item.range().start(), item.range().end(), item.range().startsAt(),
                        item.range().endsAt());
            }
            if (agreedDates.contains(item.range().end())) {
                row.publishFromImage();
            }
        }
        // 다시 읽었더니 사라진 후보는 지운다.
        String prefix = "notice:" + articleNo + ":img.";
        dateItems.findByNoticeIdOrderByEndsOnAsc(noticeId).stream()
                .filter(i -> DateItem.STATUS_CANDIDATE.equals(i.getStatus()) && i.getNaturalKey().startsWith(prefix)
                        && !seen.contains(i.getNaturalKey()))
                .forEach(dateItems::delete);
        log.info("notice {} image -> {} new, {} agreed dates", articleNo, added, agreedDates.size());
        return added;
    }

    /** 본문을 읽지 못한 글도 검수 큐에 넣는다. 조용히 건너뛰면 아무도 모른다. */
    @Transactional
    public void saveFailure(NoticeSummary summary, String html, String error) {
        if (notices.existsByArticleNo(summary.articleNo())) {
            return;
        }
        if (html != null) {
            raws.save(new NoticeRaw(summary.articleNo(), html));
        } else {
            raws.save(new NoticeRaw(summary.articleNo(), ""));
        }
        notices.save(new Notice(summary.articleNo(), summary.category(), summary.title(), summary.postedOn(), "",
                summary.url() == null ? props.noticeViewUrl(summary.articleNo()) : summary.url(), "읽기 실패: " + error, false, "",
                Notice.REASON_PARSE_FAILED));
        log.warn("notice {} parse failed: {}", summary.articleNo(), error);
    }
}
