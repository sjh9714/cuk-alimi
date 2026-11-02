package com.cukalimi.api;

import com.cukalimi.auth.AuthService;
import com.cukalimi.domain.AppUser;
import com.cukalimi.domain.Benefit;
import com.cukalimi.domain.BenefitRepository;
import com.cukalimi.domain.DateItem;
import com.cukalimi.domain.DateItemRepository;
import com.cukalimi.domain.DateReport;
import com.cukalimi.domain.DateReportRepository;
import com.cukalimi.domain.Notice;
import com.cukalimi.domain.NoticeRepository;
import com.cukalimi.domain.SavedItem;
import com.cukalimi.domain.SavedItemRepository;
import com.cukalimi.domain.UserBenefit;
import com.cukalimi.domain.UserBenefitRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 읽기는 누구나. 저장·체크는 로그인한 사람만. */
@RestController
@RequestMapping("/api")
public class ItemsController {

    /** 한 사람이 하루에 남길 수 있는 신고 수. 한 사람이 날짜를 다 내리지 못하게. */
    static final int DAILY_REPORTS = 10;

    private final DateReportRepository reports;
    private final Clock clock;
    private final AuthService auth;
    private final DateItemRepository dateItems;
    private final NoticeRepository notices;
    private final BenefitRepository benefits;
    private final SavedItemRepository saved;
    private final UserBenefitRepository userBenefits;

    public ItemsController(AuthService auth, DateItemRepository dateItems, NoticeRepository notices,
            BenefitRepository benefits, SavedItemRepository saved, UserBenefitRepository userBenefits,
            DateReportRepository reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
        this.auth = auth;
        this.dateItems = dateItems;
        this.notices = notices;
        this.benefits = benefits;
        this.saved = saved;
        this.userBenefits = userBenefits;
    }

    public record NoticeRef(long articleNo, String title, String url, String category, String reviewReason, String status) {
    }

    /**
     * bucket. 0 = 내 프로필과 맞음, 1 = 전체 대상, 2 = 대상 확인 필요. 숨기는 것은 없다 (ADR 008).
     * reviewed = 사람이 확인한 항목. 아니면 "검수 전"으로 보여준다.
     * fromImage = 그림을 받아 적은 글에서 읽은 후보. 검수 화면에만 나온다 (ADR 010).
     */
    public record ItemView(long id, String title, LocalDate startsOn, LocalDate endsOn, LocalTime startsAt,
            LocalTime endsAt, String sourceKind, boolean reviewed, String targetGrade, String targetDept,
            String targetNote, String campus, NoticeRef notice, int bucket, boolean saved, boolean fromImage) {
    }

    public record BenefitView(long id, String slug, String name, String category, String summary, String condition,
            String howTo, String price, String sourceName, String sourceUrl, LocalDate verifiedOn, String renewalRule,
            LocalDate endsOn, boolean stale, boolean checked) {
    }

    @GetMapping("/items")
    @Transactional(readOnly = true)
    public List<ItemView> items(HttpServletRequest request,
            @RequestParam(defaultValue = "false") boolean includePast) {
        LocalDate today = LocalDate.now();
        AppUser user = auth.currentUser(request).orElse(null);
        Integer grade = user == null ? null : user.grade(today);
        String major = user == null ? null : user.getMajor();
        String second = user == null ? null : user.getSecondMajor();
        Set<Long> savedIds = user == null ? Set.of()
                : saved.findByUserId(user.getId()).stream().map(SavedItem::getDateItemId).collect(Collectors.toSet());

        List<DateItem> items = includePast ? dateItems.findByStatusNot(DateItem.STATUS_CANDIDATE)
                : dateItems.findByEndsOnGreaterThanEqualAndStatusNot(today, DateItem.STATUS_CANDIDATE);
        Map<Long, Notice> noticeById = new HashMap<>();
        for (Notice n : notices.findAllById(items.stream().map(DateItem::getNoticeId).filter(id -> id != null).toList())) {
            noticeById.put(n.getId(), n);
        }
        return items.stream()
                .map(i -> toView(i, noticeById.get(i.getNoticeId()), grade, major, second, savedIds.contains(i.getId())))
                .sorted(Comparator.comparingInt(ItemView::bucket).thenComparing(ItemView::endsOn)
                        .thenComparing(ItemView::title))
                .toList();
    }

    static ItemView toView(DateItem i, Notice n, Integer grade, String major, String second, boolean isSaved) {
        NoticeRef ref = n == null ? null
                : new NoticeRef(n.getArticleNo(), n.getTitle(), n.getUrl(), n.getCategory(), n.getReviewReason(), n.getStatus());
        return new ItemView(i.getId(), i.getTitle(), i.getStartsOn(), i.getEndsOn(), i.getStartsAt(), i.getEndsAt(),
                i.getSourceKind(), DateItem.STATUS_REVIEWED.equals(i.getStatus()), i.getTargetGrade(), i.getTargetDept(),
                i.getTargetNote(), i.getCampus(), ref, bucket(i, grade, major, second), isSaved,
                DateItem.STATUS_CANDIDATE.equals(i.getStatus()) || DateItem.STATUS_AUTO_IMAGE.equals(i.getStatus()));
    }

    static int bucket(DateItem i, Integer grade, String major, String second) {
        String g = i.getTargetGrade();
        String d = i.getTargetDept();
        if (g == null || d == null) {
            return 2;
        }
        boolean gradeMatch = "ALL".equals(g) || (grade != null && g.equals(String.valueOf(grade)));
        boolean deptMatch = "ALL".equals(d) || (major != null && d.contains(major)) || (second != null && d.contains(second));
        if (gradeMatch && deptMatch && !("ALL".equals(g) && "ALL".equals(d))) {
            return 0;
        }
        if ("ALL".equals(g) && "ALL".equals(d)) {
            return 1;
        }
        return gradeMatch && deptMatch ? 0 : 1;
    }

    @GetMapping("/benefits")
    @Transactional(readOnly = true)
    public List<BenefitView> benefits(HttpServletRequest request) {
        LocalDate today = LocalDate.now();
        AppUser user = auth.currentUser(request).orElse(null);
        Set<Long> checked = user == null ? Set.of()
                : userBenefits.findByUserId(user.getId()).stream().map(UserBenefit::getBenefitId).collect(Collectors.toSet());
        return benefits.findByActiveTrueOrderByCategoryAscNameAsc().stream()
                .map(b -> new BenefitView(b.getId(), b.getSlug(), b.getName(), b.getCategory(), b.getSummary(),
                        b.getCondition(), b.getHowTo(), b.getPrice(), b.getSourceName(), b.getSourceUrl(),
                        b.getVerifiedOn(), b.getRenewalRule(), b.getEndsOn(), b.isStale(today), checked.contains(b.getId())))
                .toList();
    }

    /** 자동 공개한 그림 날짜가 원문과 다르다는 신고. 바로 내려 운영자에게 넘긴다. 사람이 확인한 날짜는 기록만 한다. */
    @PostMapping("/items/{itemId}/report")
    @Transactional
    public ResponseEntity<Void> report(HttpServletRequest request, @PathVariable long itemId) {
        AppUser user = auth.requireUser(request);
        DateItem item = dateItems.findById(itemId).orElseThrow();
        if (reports.existsByDateItemIdAndUserId(itemId, user.getId())) {
            return ResponseEntity.noContent().build();
        }
        if (reports.countByUserIdAndCreatedAtAfter(user.getId(), OffsetDateTime.now(clock).minusDays(1)) >= DAILY_REPORTS) {
            throw new IllegalArgumentException("오늘은 더 신고할 수 없어요. 내일 다시 시도해 주세요");
        }
        reports.save(new DateReport(itemId, user.getId()));
        item.withdrawFromImage();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/saved/{itemId}")
    @Transactional
    public ResponseEntity<Void> save(HttpServletRequest request, @PathVariable long itemId) {
        AppUser user = auth.requireUser(request);
        dateItems.findById(itemId).orElseThrow();
        saved.save(new SavedItem(user.getId(), itemId));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/saved/{itemId}")
    @Transactional
    public ResponseEntity<Void> unsave(HttpServletRequest request, @PathVariable long itemId) {
        AppUser user = auth.requireUser(request);
        saved.deleteById(new SavedItem.Key(user.getId(), itemId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/benefits/{benefitId}")
    @Transactional
    public ResponseEntity<Void> check(HttpServletRequest request, @PathVariable long benefitId) {
        AppUser user = auth.requireUser(request);
        Benefit b = benefits.findById(benefitId).orElseThrow();
        userBenefits.save(new UserBenefit(user.getId(), b.getId()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/benefits/{benefitId}")
    @Transactional
    public ResponseEntity<Void> uncheck(HttpServletRequest request, @PathVariable long benefitId) {
        AppUser user = auth.requireUser(request);
        userBenefits.deleteById(new UserBenefit.Key(user.getId(), benefitId));
        return ResponseEntity.noContent().build();
    }
}
