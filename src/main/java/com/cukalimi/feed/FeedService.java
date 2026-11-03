package com.cukalimi.feed;

import com.cukalimi.config.AppProperties;
import com.cukalimi.domain.AppUser;
import com.cukalimi.domain.DateItem;
import com.cukalimi.domain.DateItemRepository;
import com.cukalimi.domain.Notice;
import com.cukalimi.domain.NoticeRepository;
import com.cukalimi.domain.SavedItem;
import com.cukalimi.domain.SavedItemRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 한 사람의 홈 피드. 홈 화면과 아침 알림이 같은 것을 본다. */
@Service
public class FeedService {

    private final AppProperties props;
    private final DateItemRepository dateItems;
    private final NoticeRepository notices;
    private final SavedItemRepository saved;

    public FeedService(AppProperties props, DateItemRepository dateItems, NoticeRepository notices, SavedItemRepository saved) {
        this.props = props;
        this.dateItems = dateItems;
        this.notices = notices;
        this.saved = saved;
    }

    /** 피드와, 카드가 가리키는 공지. 알림은 공지가 언제 들어왔는지 봐야 한다. */
    public record Built(Feed.View view, Map<Long, Notice> notices) {
    }

    @Transactional(readOnly = true)
    public Built build(AppUser user, LocalDate today) {
        Feed.Viewer viewer = user == null ? Feed.Viewer.ANONYMOUS
                : new Feed.Viewer(user.grade(today), user.semester(today), user.getMajor(), user.getSecondMajor(),
                        Regions.parse(user.getHomeRegion()).orElse(null));
        Set<Long> savedIds = user == null ? Set.of()
                : saved.findByUserId(user.getId()).stream().map(SavedItem::getDateItemId).collect(Collectors.toSet());

        List<DateItem> upcoming = dateItems.findByEndsOnGreaterThanEqualAndStatusNot(today, DateItem.STATUS_CANDIDATE);
        Map<Long, Notice> noticeById = new HashMap<>();
        Map<Long, Audience> audiences = new HashMap<>();
        for (Notice n : notices.findAllById(upcoming.stream().map(DateItem::getNoticeId).filter(Objects::nonNull).toList())) {
            noticeById.put(n.getId(), n);
            audiences.put(n.getId(), Audience.of(n));
        }
        List<Notice> undated = undated(today);
        undated.forEach(n -> audiences.put(n.getId(), Audience.of(n)));
        return new Built(Feed.build(upcoming, noticeById, audiences, undated, viewer, user != null, savedIds,
                props::scheduleUrl), noticeById);
    }

    /**
     * 날짜가 그림에만 있고 운영자가 아직 날짜를 넣지 않은 장학·학사 공지. 검수 큐가 그림 공지를 30일 들고 있으니 같은 기간만 보여준다.
     * 그림에서 읽은 후보 날짜만 있는 공지도 승인 전까지는 여기에 둔다. 후보가 모두 지났으면 학생에게는 내리고 검수 큐에만 남긴다.
     */
    private List<Notice> undated(LocalDate today) {
        Map<Long, Notice> byId = new java.util.LinkedHashMap<>();
        notices.findByStatusAndReviewReasonAndPostedOnGreaterThanEqual(Notice.STATUS_NEEDS_REVIEW, Notice.REASON_IMAGE_ONLY,
                today.minusDays(UNDATED_DAYS)).forEach(n -> byId.put(n.getId(), n));
        // 후보 날짜가 남아 있으면 30일이 지나도 보여 준다. 승인이 밀려도 마감 전 공지가 사라지지 않게.
        notices.findAllById(dateItems.findNoticeIdsWithOpenItems(DateItem.STATUS_CANDIDATE, today))
                .forEach(n -> byId.putIfAbsent(n.getId(), n));
        List<Notice> imageOnly = byId.values().stream().filter(FeedService::undatedCategory).toList();
        if (imageOnly.isEmpty()) {
            return imageOnly;
        }
        // 공지마다 묻지 않고 한 번에 묻는다. 아침 메일은 사용자마다 피드를 만든다.
        Set<Long> dated = dateItems.findNoticeIdsWithItemsNotIn(imageOnly.stream().map(Notice::getId).toList(),
                DateItem.STATUS_CANDIDATE);
        Map<Long, List<DateItem>> candidates = dateItems.findByNoticeIdIn(imageOnly.stream().map(Notice::getId).toList())
                .stream().collect(Collectors.groupingBy(DateItem::getNoticeId));
        return imageOnly.stream()
                .filter(n -> !dated.contains(n.getId()))
                .filter(n -> !candidates.containsKey(n.getId())
                        || candidates.get(n.getId()).stream().anyMatch(i -> !i.getEndsOn().isBefore(today)))
                .toList();
    }

    /** 취업 공지는 기업 포스터가 많아 넣지 않는다. 그림 받아 적기도 같은 공지만 본다 (ADR 010). */
    public static boolean undatedCategory(Notice n) {
        return "장학".equals(n.getCategory()) || "학사".equals(n.getCategory());
    }

    public static final int UNDATED_DAYS = 30;
}
