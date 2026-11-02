package com.campusalimi.digest;

import com.campusalimi.auth.MailSender;
import com.campusalimi.config.AppProperties;
import com.campusalimi.domain.AppUser;
import com.campusalimi.domain.DateItemRepository;
import com.campusalimi.domain.DateItem;
import com.campusalimi.domain.AppUserRepository;
import com.campusalimi.domain.Benefit;
import com.campusalimi.domain.BenefitRepository;
import com.campusalimi.domain.Notice;
import com.campusalimi.domain.SentNotification;
import com.campusalimi.domain.SentNotificationRepository;
import com.campusalimi.domain.UserBenefit;
import com.campusalimi.domain.UserBenefitRepository;
import com.campusalimi.feed.Feed;
import com.campusalimi.feed.FeedService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아침 알림 메일 (ADR 004). 하루 한 통에 새 공지, 저장한 마감, 체크한 혜택 만료를 묶는다.
 * 사건마다 키를 두고 보낸 키는 sent_notification에 남겨 두 번 보내지 않는다. 보낼 것이 없으면 보내지 않는다.
 */
@Service
public class DigestService {

    private static final Logger log = LoggerFactory.getLogger(DigestService.class);

    /** 새 공지로 치는 기간. 더 오래된 공지는 처음 가입한 사람에게도 보내지 않는다. */
    static final Duration NEW_WINDOW = Duration.ofHours(48);
    static final int SAVED_DAYS = 3;
    static final int BENEFIT_DAYS = 7;
    private static final Set<String> NEW_SECTIONS = Set.of("must", "mine", "open");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M.d");
    private static final String[] WEEKDAY = { "월", "화", "수", "목", "금", "토", "일" };

    private final AppProperties props;
    private final AppUserRepository users;
    private final FeedService feeds;
    private final BenefitRepository benefits;
    private final UserBenefitRepository userBenefits;
    private final SentNotificationRepository sent;
    private final MailSender mail;
    private final DateItemRepository dateItems;
    private final Clock clock;

    public DigestService(AppProperties props, AppUserRepository users, FeedService feeds, BenefitRepository benefits,
            UserBenefitRepository userBenefits, SentNotificationRepository sent, MailSender mail,
            DateItemRepository dateItems, Clock clock) {
        this.props = props;
        this.users = users;
        this.feeds = feeds;
        this.benefits = benefits;
        this.userBenefits = userBenefits;
        this.sent = sent;
        this.mail = mail;
        this.dateItems = dateItems;
        this.clock = clock;
    }

    /** 메일 한 통의 내용. 비어 있으면 보내지 않는다. */
    public record Digest(List<Feed.Card> newNotices, List<SavedLine> savedSoon, List<Benefit> benefitsEnding) {
        public boolean isEmpty() {
            return newNotices.isEmpty() && savedSoon.isEmpty() && benefitsEnding.isEmpty();
        }

        List<String> keys() {
            List<String> keys = new ArrayList<>();
            newNotices.forEach(c -> keys.add(c.key()));
            savedSoon.forEach(s -> keys.add("saved:" + s.line().id()));
            benefitsEnding.forEach(b -> keys.add("benefit:" + b.getId()));
            return keys;
        }
    }

    public record SavedLine(Feed.Card card, Feed.Line line) {
    }

    public record Result(int users, int sent) {
    }

    @Scheduled(cron = "${app.digest.cron}", zone = "Asia/Seoul")
    public void sendScheduled() {
        try {
            Result r = sendAll();
            log.info("digest users={} sent={}", r.users(), r.sent());
        } catch (RuntimeException e) {
            log.error("digest failed", e);
        }
        try {
            sendReviewReminder();
        } catch (RuntimeException e) {
            log.error("review reminder failed", e);
        }
    }

    /** 승인을 기다리는 그림 공지가 있으면 운영자에게 알린다. 승인이 밀리면 학생이 날짜를 못 보기 때문이다. */
    public boolean sendReviewReminder() {
        if (props.adminEmail() == null || props.adminEmail().isBlank()) {
            return false;
        }
        long pending = dateItems.countNoticesWithOpenItems(DateItem.STATUS_CANDIDATE, LocalDate.now(clock));
        if (pending == 0) {
            return false;
        }
        mail.send(props.adminEmail(), "[CUK-Alimi] 승인 대기 " + pending + "건",
                "그림에서 읽은 날짜가 승인을 기다리는 공지가 " + pending + "건 있어요. 승인 전에는 학생에게 날짜가 보이지 않아요.\n\n"
                        + "검수하기: " + props.publicUrl() + "/admin/\n");
        return true;
    }

    /** 한 사람이 실패해도 나머지는 간다. */
    public Result sendAll() {
        List<AppUser> all = users.findByDigestEnabledTrue();
        int sentCount = 0;
        for (AppUser user : all) {
            try {
                if (sendTo(user)) {
                    sentCount++;
                }
            } catch (RuntimeException e) {
                log.error("digest to user {} failed", user.getId(), e);
            }
        }
        return new Result(all.size(), sentCount);
    }

    /** 보냈으면 true. 보낸 키는 메일이 나간 뒤에만 남긴다. 메일이 실패하면 다음 날 다시 시도한다. */
    @Transactional
    public boolean sendTo(AppUser user) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Digest digest = build(user, now);
        if (digest.isEmpty()) {
            return false;
        }
        mail.send(user.getEmail(), subject(now.toLocalDate()), body(user, digest, now.toLocalDate()));
        for (String key : digest.keys()) {
            sent.save(new SentNotification(user.getId(), key));
        }
        return true;
    }

    @Transactional(readOnly = true)
    public Digest build(AppUser user, OffsetDateTime now) {
        LocalDate today = now.toLocalDate();
        Set<String> already = sent.findByUserId(user.getId()).stream().map(SentNotification::getEventKey)
                .collect(Collectors.toSet());
        FeedService.Built built = feeds.build(user, today);
        OffsetDateTime since = now.minus(NEW_WINDOW);

        List<Feed.Card> newNotices = new ArrayList<>();
        List<SavedLine> savedSoon = new ArrayList<>();
        for (Feed.Section section : built.view().sections()) {
            for (Feed.Card card : section.cards()) {
                if (card.deadline() == null) {
                    // 날짜 확인 중인 공지. 날짜 없는 공지는 알림에 넣지 않는다.
                    continue;
                }
                Notice notice = noticeOf(card, built);
                if (NEW_SECTIONS.contains(section.key()) && notice != null && !already.contains(card.key())
                        && notice.getFirstSeenAt() != null && notice.getFirstSeenAt().isAfter(since)) {
                    newNotices.add(card);
                }
                List<Feed.Line> lines = new ArrayList<>(card.next());
                lines.add(0, card.deadline());
                for (Feed.Line line : lines) {
                    if (line.saved() && "ACTION".equals(line.kind()) && !already.contains("saved:" + line.id())
                            && !line.endsOn().isAfter(today.plusDays(SAVED_DAYS))) {
                        savedSoon.add(new SavedLine(card, line));
                    }
                }
            }
        }

        Set<Long> checked = userBenefits.findByUserId(user.getId()).stream().map(UserBenefit::getBenefitId)
                .collect(Collectors.toSet());
        List<Benefit> ending = benefits.findByActiveTrueOrderByCategoryAscNameAsc().stream()
                .filter(b -> checked.contains(b.getId()) && b.getEndsOn() != null && !already.contains("benefit:" + b.getId())
                        && !b.getEndsOn().isBefore(today) && !b.getEndsOn().isAfter(today.plusDays(BENEFIT_DAYS)))
                .toList();
        return new Digest(newNotices, savedSoon, ending);
    }

    private static Notice noticeOf(Feed.Card card, FeedService.Built built) {
        if (!card.key().startsWith("notice:")) {
            return null;
        }
        return built.notices().get(Long.parseLong(card.key().substring("notice:".length())));
    }

    static String subject(LocalDate today) {
        return "[CUK-Alimi] " + today.getMonthValue() + "월 " + today.getDayOfMonth() + "일 아침 알림";
    }

    String body(AppUser user, Digest d, LocalDate today) {
        StringBuilder sb = new StringBuilder();
        if (!d.newNotices().isEmpty()) {
            sb.append("■ 새로 올라온 공지 ").append(d.newNotices().size()).append("\n");
            for (Feed.Card c : d.newNotices()) {
                sb.append(cardLine(c, c.deadline(), today));
            }
            sb.append("\n");
        }
        if (!d.savedSoon().isEmpty()) {
            sb.append("■ 저장한 마감 ").append(SAVED_DAYS).append("일 안 ").append(d.savedSoon().size()).append("\n");
            for (SavedLine s : d.savedSoon()) {
                sb.append(cardLine(s.card(), s.line(), today));
            }
            sb.append("\n");
        }
        if (!d.benefitsEnding().isEmpty()) {
            sb.append("■ 체크한 혜택 만료 ").append(BENEFIT_DAYS).append("일 안 ").append(d.benefitsEnding().size()).append("\n");
            for (Benefit b : d.benefitsEnding()) {
                sb.append("- ").append(when(b.getEndsOn(), today)).append(" · ").append(b.getName()).append("\n  ")
                        .append(b.getSourceUrl()).append("\n");
            }
            sb.append("\n");
        }
        sb.append("전체 보기: ").append(props.publicUrl()).append("/\n");
        sb.append("알림 끄기: ").append(props.apiUrlOrPublic()).append("/api/digest/unsubscribe?token=")
                .append(user.getUnsubscribeToken()).append("\n");
        return sb.toString();
    }

    private static String cardLine(Feed.Card c, Feed.Line line, LocalDate today) {
        StringBuilder sb = new StringBuilder("- ").append(when(line.endsOn(), today)).append(" · ").append(c.title());
        if (line.label() != null) {
            sb.append(" · ").append(line.label());
        }
        if (line.endsAt() != null) {
            sb.append(" ").append(line.endsAt().toString(), 0, 5);
        }
        return sb.append("\n  ").append(c.url()).append("\n").toString();
    }

    private static String when(LocalDate d, LocalDate today) {
        long days = Duration.between(today.atStartOfDay(), d.atStartOfDay()).toDays();
        String dday = days < 0 ? "지남" : days == 0 ? "오늘" : "D-" + days;
        return d.format(DAY) + "(" + WEEKDAY[d.getDayOfWeek().getValue() - 1] + ") " + dday;
    }
}
