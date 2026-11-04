package com.cukalimi.push;

import com.cukalimi.config.AppProperties;
import com.cukalimi.domain.AppUser;
import com.cukalimi.domain.AppUserRepository;
import com.cukalimi.domain.PushSubscription;
import com.cukalimi.domain.PushSubscriptionRepository;
import com.cukalimi.domain.SentNotification;
import com.cukalimi.domain.SentNotificationRepository;
import com.cukalimi.feed.Feed;
import com.cukalimi.feed.FeedService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마감 푸시 (ADR 004). 아침 8시에 내게 맞는 일과 저장한 일의 D-3, D-1을 기기마다 한 번 보낸다.
 * 메일은 "오늘 뭐가 있나"이고 푸시는 "이거 곧 마감"이다. 사건 키는 sent_notification에 남겨 두 번 가지 않는다.
 * 끝냈어요로 체크한 줄은 보내지 않는다. 푸시 서비스가 404나 410을 돌려주면 그 구독은 지운다.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    static final int[] DAYS_BEFORE = { 3, 1 };
    private static final Duration TTL = Duration.ofHours(12);

    /** 보내는 쪽. 시험에서는 가짜로 바꾼다. */
    public interface Sender {
        WebPush.Outcome send(WebPush.Subscription sub, String payloadJson);

        String publicKey();

        boolean enabled();
    }

    public record Reminder(long lineId, int daysBefore, String title, String body, String url) {
        public String key() {
            return "push:" + lineId + ":" + daysBefore;
        }
    }

    public record Result(int users, int sent, int gone) {
    }

    private final AppProperties props;
    private final AppUserRepository users;
    private final PushSubscriptionRepository subscriptions;
    private final SentNotificationRepository sent;
    private final FeedService feeds;
    private final Sender sender;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    public PushService(AppProperties props, AppUserRepository users, PushSubscriptionRepository subscriptions,
            SentNotificationRepository sent, FeedService feeds, Sender sender, Clock clock) {
        this.props = props;
        this.users = users;
        this.subscriptions = subscriptions;
        this.sent = sent;
        this.feeds = feeds;
        this.sender = sender;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.push.cron}", zone = "Asia/Seoul")
    public void sendScheduled() {
        if (!sender.enabled()) {
            return;
        }
        try {
            Result r = sendAll();
            log.info("push users={} sent={} gone={}", r.users(), r.sent(), r.gone());
        } catch (RuntimeException e) {
            log.error("push failed", e);
        }
    }

    /** 구독이 있는 사람만 돈다. 한 사람이 실패해도 나머지는 간다. */
    public Result sendAll() {
        List<Long> userIds = subscriptions.findDistinctUserIds();
        int sentCount = 0;
        int gone = 0;
        for (Long userId : userIds) {
            try {
                AppUser user = users.findById(userId).orElse(null);
                if (user == null) {
                    continue;
                }
                Result r = sendTo(user);
                sentCount += r.sent();
                gone += r.gone();
            } catch (RuntimeException e) {
                log.error("push to user {} failed", userId, e);
            }
        }
        return new Result(userIds.size(), sentCount, gone);
    }

    /** 오늘 보낼 알림을 구독한 기기마다 보낸다. 한 기기라도 받았으면 키를 남긴다. */
    @Transactional
    public Result sendTo(AppUser user) {
        List<PushSubscription> subs = subscriptions.findByUserId(user.getId());
        if (subs.isEmpty()) {
            return new Result(1, 0, 0);
        }
        List<Reminder> reminders = remindersFor(user, LocalDate.now(clock));
        int sentCount = 0;
        int gone = 0;
        for (Reminder reminder : reminders) {
            String payload = payload(reminder);
            boolean delivered = false;
            for (PushSubscription sub : new ArrayList<>(subs)) {
                WebPush.Outcome outcome = sender.send(new WebPush.Subscription(sub.getEndpoint(), sub.getP256dh(), sub.getAuth()),
                        payload);
                if (outcome == WebPush.Outcome.GONE) {
                    subscriptions.delete(sub);
                    subs.remove(sub);
                    gone++;
                } else if (outcome == WebPush.Outcome.SENT) {
                    delivered = true;
                    sentCount++;
                }
            }
            if (delivered) {
                sent.save(new SentNotification(user.getId(), reminder.key()));
            }
        }
        return new Result(1, sentCount, gone);
    }

    /** 내게 맞는 카드의 모든 줄과 저장한 줄 가운데 D-3, D-1인 것. 끝낸 줄과 이미 보낸 키는 뺀다. */
    @Transactional(readOnly = true)
    public List<Reminder> remindersFor(AppUser user, LocalDate today) {
        Set<String> already = sent.findByUserId(user.getId()).stream().map(SentNotification::getEventKey)
                .collect(Collectors.toSet());
        Map<String, Reminder> out = new LinkedHashMap<>();
        Feed.View view = feeds.build(user, today).view();
        for (Feed.Section section : view.sections()) {
            for (Feed.Card card : section.cards()) {
                // 홈은 같은 공지의 같은 날 줄을 한 행으로 합치고, 체크도 그 행 단위다. 알림도 같은 단위로 보낸다.
                for (List<Feed.Line> group : Feed.sameDayGroups(card)) {
                    Feed.Line line = group.get(0);
                    boolean anyDone = group.stream().anyMatch(Feed.Line::done);
                    boolean wanted = card.mine() || group.stream().anyMatch(Feed.Line::saved);
                    if (anyDone || !wanted) {
                        continue;
                    }
                    for (int days : DAYS_BEFORE) {
                        if (line.endsOn().equals(today.plusDays(days))) {
                            Reminder r = reminder(card, line, group.size() > 1 ? null : line.label(), days);
                            if (!already.contains(r.key())) {
                                out.putIfAbsent(r.key(), r);
                            }
                        }
                    }
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    private static Reminder reminder(Feed.Card card, Feed.Line line, String label, int days) {
        String what = label == null || label.isBlank() ? card.title() : card.title() + " · " + label;
        String when = days == 1 ? "내일" : days + "일 뒤";
        String time = line.endsAt() == null ? "" : " " + line.endsAt().toString().substring(0, 5) + "까지";
        return new Reminder(line.id(), days, when + " 마감" + time, what, card.url());
    }

    String payload(Reminder r) {
        try {
            return json.writeValueAsString(Map.of("title", r.title(), "body", r.body(), "url", r.url(), "tag", r.key()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public AppProperties props() {
        return props;
    }

    /** 운영 전송. VAPID 키가 없으면 꺼진다. */
    @org.springframework.stereotype.Component
    public static class WebPushSender implements Sender {
        private final WebPush push;

        public WebPushSender(AppProperties props) {
            AppProperties.Push p = props.push();
            this.push = p == null || !p.enabled() ? null : new WebPush(p.publicKey(), p.privateKey(), p.subject());
        }

        @Override
        public WebPush.Outcome send(WebPush.Subscription sub, String payloadJson) {
            return push == null ? WebPush.Outcome.FAILED : push.send(sub, payloadJson, TTL);
        }

        @Override
        public String publicKey() {
            return push == null ? null : push.publicKey();
        }

        @Override
        public boolean enabled() {
            return push != null;
        }
    }
}
