package com.cukalimi.api;

import com.cukalimi.auth.AuthService;
import com.cukalimi.domain.AppUser;
import com.cukalimi.domain.DateItem;
import com.cukalimi.domain.DateItemRepository;
import com.cukalimi.domain.Notice;
import com.cukalimi.domain.NoticeRepository;
import com.cukalimi.domain.ReviewEvent;
import com.cukalimi.domain.ReviewEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cukalimi.feed.Audience;
import com.cukalimi.feed.Feed;
import com.cukalimi.feed.Titles;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 검수 화면의 API. 관리자 이메일 한 명만. 날짜 항목을 확인·수정·추가하고 대상 태그를 붙인다 (ADR 003, 008). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuthService auth;
    private final NoticeRepository notices;
    private final DateItemRepository dateItems;
    private final ReviewEventRepository reviewEvents;
    private final ObjectMapper json;
    private final Clock clock;

    public AdminController(AuthService auth, NoticeRepository notices, DateItemRepository dateItems,
            ReviewEventRepository reviewEvents, ObjectMapper json, Clock clock) {
        this.clock = clock;
        this.auth = auth;
        this.notices = notices;
        this.dateItems = dateItems;
        this.reviewEvents = reviewEvents;
        this.json = json;
    }

    /** 검수 기록에 남기는 항목 값. status는 고치기 전이 AUTO(글에서 읽음)인지 CANDIDATE(그림에서 읽음)인지 가른다. */
    record ItemSnapshot(String naturalKey, String sourceKind, String status, String title, LocalDate startsOn,
            LocalDate endsOn, LocalTime startsAt, LocalTime endsAt, String targetGrade) {
        static ItemSnapshot of(DateItem i) {
            return new ItemSnapshot(i.getNaturalKey(), i.getSourceKind(), i.getStatus(), i.getTitle(), i.getStartsOn(),
                    i.getEndsOn(), i.getStartsAt(), i.getEndsAt(), i.getTargetGrade());
        }
    }

    private void record(Long noticeId, Long itemId, String action, ItemSnapshot before, ItemSnapshot after, String by) {
        reviewEvents.save(new ReviewEvent(noticeId, itemId, action, write(before), write(after), by));
    }

    private String write(ItemSnapshot snapshot) {
        try {
            return snapshot == null ? null : json.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * priority. 0 = 모든 재학생 대상(수강신청·등록금·국가장학금), 1 = 일반 공지, 2 = 지역·특정 대상(볼 사람이 적다).
     * audienceOverridden이면 운영자가 범위를 고친 것이다.
     */
    public record ReviewNotice(long id, long articleNo, String category, String title, String shortTitle,
            LocalDate postedOn, String url, String reviewReason, boolean hasImages, String attachments, String status,
            String audienceScope, String audienceLabel, String audienceExcerpt, boolean audienceOverridden, int priority,
            List<ItemsController.ItemView> items) {
    }

    /** closed는 사람이 볼 필요가 없어 큐에서 뺀 공지 수. PAST(날짜가 다 지남), RESULT(결과 발표), STALE(오래된 날짜 없는 글). */
    public record ReviewQueue(List<ReviewNotice> notices, Map<String, Integer> closed) {
    }

    public record AudienceEdit(String scope, String label) {
    }

    public record ItemEdit(String title, LocalDate startsOn, LocalDate endsOn, LocalTime startsAt, LocalTime endsAt,
            String targetGrade, String targetDept, String targetNote) {
        void validate() {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("제목이 비었습니다");
            }
            if (endsOn == null) {
                throw new IllegalArgumentException("마감일이 비었습니다. 모르면 항목을 지우고 공지만 검수 완료로 두세요");
            }
            if (startsOn != null && startsOn.isAfter(endsOn)) {
                throw new IllegalArgumentException("시작이 마감보다 늦습니다");
            }
            if (targetGrade != null && !List.of("ALL", "1", "2", "3", "4").contains(targetGrade)) {
                throw new IllegalArgumentException("학년은 ALL, 1, 2, 3, 4 중 하나");
            }
        }
    }

    @GetMapping("/review")
    @Transactional(readOnly = true)
    public ReviewQueue review(HttpServletRequest request) {
        auth.requireAdmin(request);
        LocalDate today = LocalDate.now(clock);
        Map<String, Integer> closed = new LinkedHashMap<>(Map.of(CLOSED_PAST, 0, CLOSED_RESULT, 0, CLOSED_STALE, 0));
        List<ReviewNotice> queue = new java.util.ArrayList<>();
        for (Notice n : notices.findByStatusOrderByPostedOnDesc(Notice.STATUS_NEEDS_REVIEW)) {
            if (n.isRemoved()) {
                continue;
            }
            Audience audience = Audience.of(n);
            List<DateItem> items = dateItems.findByNoticeIdOrderByEndsOnAsc(n.getId());
            String reason = closedReason(n, audience, items, today);
            if (reason != null) {
                closed.merge(reason, 1, Integer::sum);
            } else {
                queue.add(toReview(n, audience, items));
            }
        }
        queue.sort(Comparator.comparingInt(ReviewNotice::priority)
                .thenComparing(r -> nextDeadline(r, today))
                .thenComparing(ReviewNotice::postedOn, Comparator.reverseOrder()));
        return new ReviewQueue(queue, closed);
    }

    static final String CLOSED_PAST = "PAST";
    static final String CLOSED_RESULT = "RESULT";
    static final String CLOSED_STALE = "STALE";

    /**
     * 검수할 필요가 없는 공지. 학생에게 나갈 날짜가 없으면 사람이 볼 이유도 없다.
     * 날짜를 못 읽은 공지는 그림 속 날짜일 수 있어 30일, 글자만 있는 안내는 14일 동안 큐에 둔다.
     */
    static String closedReason(Notice n, Audience audience, List<DateItem> items, LocalDate today) {
        if (Audience.RESULT.equals(audience.scope())) {
            return CLOSED_RESULT;
        }
        // 아직 지나지 않은 후보가 있으면 다른 날짜가 다 지나도 닫지 않는다. 신고로 내린 날짜가 큐에서 사라지지 않게.
        if (items.stream().anyMatch(i -> DateItem.STATUS_CANDIDATE.equals(i.getStatus()) && !i.getEndsOn().isBefore(today))) {
            return null;
        }
        // 그림에서 읽은 후보는 운영자가 아직 보지 않은 날짜다. 후보만 있는 공지는 날짜 없는 공지처럼 다룬다.
        List<DateItem> settled = items.stream().filter(i -> !DateItem.STATUS_CANDIDATE.equals(i.getStatus())).toList();
        if (!settled.isEmpty()) {
            return settled.stream().allMatch(i -> i.getEndsOn().isBefore(today)) ? CLOSED_PAST : null;
        }
        int keepDays = Notice.REASON_NO_DATE.equals(n.getReviewReason()) ? 14 : 30;
        return n.getPostedOn() != null && n.getPostedOn().isBefore(today.minusDays(keepDays)) ? CLOSED_STALE : null;
    }

    /** 날짜가 없는 공지(그림 속 날짜)는 사람이 넣기 전까지 학생에게 안 보이니 같은 등급 안에서 먼저 본다. */
    private static LocalDate nextDeadline(ReviewNotice r, LocalDate today) {
        return r.items().stream().map(ItemsController.ItemView::endsOn).filter(d -> !d.isBefore(today)).findFirst()
                .orElse(LocalDate.MIN);
    }

    @PutMapping("/notices/{noticeId}/audience")
    @Transactional
    public ReviewNotice editAudience(HttpServletRequest request, @PathVariable long noticeId, @RequestBody AudienceEdit body) {
        auth.requireAdmin(request);
        String scope = body.scope() == null || body.scope().isBlank() ? null : body.scope();
        if (scope != null && !Audience.SCOPES.contains(scope)) {
            throw new IllegalArgumentException("범위는 " + String.join(", ", Audience.SCOPES) + " 중 하나");
        }
        Notice notice = notices.findById(noticeId).orElseThrow();
        notice.overrideAudience(scope, body.label() == null || body.label().isBlank() ? null : body.label().trim());
        return toReview(notice);
    }

    @GetMapping("/notices/{noticeId}")
    @Transactional(readOnly = true)
    public ReviewNotice one(HttpServletRequest request, @PathVariable long noticeId) {
        auth.requireAdmin(request);
        return toReview(notices.findById(noticeId).orElseThrow());
    }

    @PostMapping("/notices/{noticeId}/reviewed")
    @Transactional
    public ReviewNotice markReviewed(HttpServletRequest request, @PathVariable long noticeId) {
        AppUser admin = auth.requireAdmin(request);
        Notice notice = notices.findById(noticeId).orElseThrow();
        notice.markReviewed(admin.getEmail());
        // 공지를 검수 완료로 두면 그 아래 자동 항목과 그림에서 읽은 후보도 확인한 것으로 본다.
        for (DateItem item : dateItems.findByNoticeIdOrderByEndsOnAsc(noticeId)) {
            if (DateItem.STATUS_AUTO.equals(item.getStatus()) || DateItem.STATUS_CANDIDATE.equals(item.getStatus())
                    || DateItem.STATUS_AUTO_IMAGE.equals(item.getStatus())) {
                ItemSnapshot before = ItemSnapshot.of(item);
                item.markReviewed(admin.getEmail());
                record(noticeId, item.getId(), ReviewEvent.APPROVE, before, ItemSnapshot.of(item), admin.getEmail());
            }
        }
        // 항목 없이 남는 공지 단위 기록. 항목이 하나도 없으면 "이 공지에는 마감이 없다"는 판단이 된다.
        record(noticeId, null, ReviewEvent.APPROVE, null, null, admin.getEmail());
        return toReview(notice);
    }

    @PutMapping("/items/{itemId}")
    @Transactional
    public ItemsController.ItemView editItem(HttpServletRequest request, @PathVariable long itemId,
            @RequestBody ItemEdit body) {
        AppUser admin = auth.requireAdmin(request);
        body.validate();
        DateItem item = dateItems.findById(itemId).orElseThrow();
        ItemSnapshot before = ItemSnapshot.of(item);
        item.review(body.title(), body.startsOn(), body.endsOn(), body.startsAt(), body.endsAt(), body.targetGrade(),
                body.targetDept(), body.targetNote(), admin.getEmail());
        record(item.getNoticeId(), item.getId(), ReviewEvent.EDIT, before, ItemSnapshot.of(item), admin.getEmail());
        Notice n = item.getNoticeId() == null ? null : notices.findById(item.getNoticeId()).orElse(null);
        return ItemsController.toView(item, n, null, null, null, false);
    }

    @PostMapping("/notices/{noticeId}/items")
    @Transactional
    public ItemsController.ItemView addItem(HttpServletRequest request, @PathVariable long noticeId,
            @RequestBody ItemEdit body) {
        AppUser admin = auth.requireAdmin(request);
        body.validate();
        Notice notice = notices.findById(noticeId).orElseThrow();
        DateItem item = dateItems.save(DateItem.manual(notice.getId(), body.title(), body.startsOn(), body.endsOn(),
                body.startsAt(), body.endsAt(), body.targetGrade(), body.targetDept(), body.targetNote(), admin.getEmail()));
        record(notice.getId(), item.getId(), ReviewEvent.ADD, null, ItemSnapshot.of(item), admin.getEmail());
        return ItemsController.toView(item, notice, null, null, null, false);
    }

    @DeleteMapping("/items/{itemId}")
    @Transactional
    public ResponseEntity<Void> deleteItem(HttpServletRequest request, @PathVariable long itemId) {
        AppUser admin = auth.requireAdmin(request);
        dateItems.findById(itemId).ifPresent(item -> {
            record(item.getNoticeId(), item.getId(), ReviewEvent.DELETE, ItemSnapshot.of(item), null, admin.getEmail());
            dateItems.delete(item);
        });
        return ResponseEntity.noContent().build();
    }

    private ReviewNotice toReview(Notice n) {
        return toReview(n, Audience.of(n), dateItems.findByNoticeIdOrderByEndsOnAsc(n.getId()));
    }

    private static ReviewNotice toReview(Notice n, Audience audience, List<DateItem> dateItemList) {
        List<ItemsController.ItemView> items = dateItemList.stream()
                .map(i -> ItemsController.toView(i, n, null, null, null, false))
                .toList();
        String shortTitle = Titles.clean(n.getTitle());
        // 취업 공지는 접힌 구간에 가고 수가 많아 지역·특정 대상과 같이 뒤로 둔다.
        int priority = Feed.isMust(audience, shortTitle) ? 0
                : Audience.GENERAL.equals(audience.scope()) && !"취창업".equals(n.getCategory()) ? 1 : 2;
        return new ReviewNotice(n.getId(), n.getArticleNo(), n.getCategory(), n.getTitle(), shortTitle, n.getPostedOn(),
                n.getUrl(), n.getReviewReason(), n.isHasImages(), n.getAttachments(), n.getStatus(), audience.scope(),
                audience.label(), audience.excerpt(), n.getAudienceScope() != null, priority, items);
    }
}
