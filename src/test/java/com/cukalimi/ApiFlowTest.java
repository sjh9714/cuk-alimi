package com.cukalimi;

import static org.assertj.core.api.Assertions.assertThat;

import com.cukalimi.auth.ConsoleMailSender;
import com.cukalimi.digest.DigestService;
import com.cukalimi.domain.AppUser;
import com.cukalimi.domain.AppUserRepository;
import com.cukalimi.domain.DateItem;
import com.cukalimi.domain.DateItemRepository;
import com.cukalimi.domain.ImageTranscriptRepository;
import com.cukalimi.domain.LoginToken;
import com.cukalimi.domain.LoginTokenRepository;
import com.cukalimi.domain.ReviewEvent;
import com.cukalimi.domain.ReviewEventRepository;
import com.cukalimi.domain.Notice;
import com.cukalimi.feed.Feed;
import com.cukalimi.domain.NoticeRepository;
import com.cukalimi.fetch.PageFetcher;
import com.cukalimi.notice.ImageNoticeReader;
import com.cukalimi.notice.ImageTranscriber;
import com.cukalimi.notice.NoticeStore;
import com.cukalimi.notice.NoticeSummary;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 로그인부터 검수까지 실제 HTTP로 한 바퀴. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = { "app.scheduling.enabled=false", "app.fetch-interval-ms=0",
        "app.admin-email=admin@catholic.ac.kr", "app.public-url=http://localhost:3000", "app.mail.mode=console" })
@SuppressWarnings({ "unchecked", "rawtypes" })
class ApiFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final java.util.concurrent.atomic.AtomicBoolean ALIVE_999303 = new java.util.concurrent.atomic.AtomicBoolean(false);

    @TestConfiguration
    static class NoNetwork {
        @Bean
        @Primary
        PageFetcher fetcher() {
            return new PageFetcher() {
                @Override
                public String get(String url) {
                    // 학교가 지운 글. 다시 열면 404다. ALIVE에 넣으면 저장해 둔 본문을 돌려준다.
                    if (url.contains("articleNo=999303")) {
                        if (ALIVE_999303.get()) {
                            return Fixtures.read("notice-273306.html").replace("273306", "999303").replace("수강신청 일정 안내", "수강신청 일정 재공지");
                        }
                        throw new java.io.UncheckedIOException(new org.jsoup.HttpStatusException("not found", 404, url));
                    }
                    throw new IllegalStateException("no network in this test");
                }

                @Override
                public byte[] getBytes(String url) {
                    // 학교 서버에서 사라진 그림. 404는 다시 받아도 소용없다.
                    if (url.contains("/gone-")) {
                        throw new java.io.UncheckedIOException(new org.jsoup.HttpStatusException("not found", 404, url));
                    }
                    // 받아 적기 테스트용 공지의 그림만 1로 표시한다. 다른 공지에는 날짜가 생기지 않게.
                    return new byte[] { (byte) (url.contains("/copy") ? 1 : 0) };
                }
            };
        }

        @Bean
        @Primary
        FakeTranscriber transcriber() {
            return new FakeTranscriber();
        }

        @Bean
        @Primary
        FakeAudienceClassifier audienceClassifier() {
            return new FakeAudienceClassifier();
        }

        @Bean
        @Primary
        FakePushSender pushSender() {
            return new FakePushSender();
        }

        /** 픽스처 공지의 날짜(2026년 7월)가 아직 남아 있는 날로 고정한다. */
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    ConsoleMailSender mail;
    @Autowired
    LoginTokenRepository loginTokens;
    @Autowired
    com.cukalimi.feed.FeedService feeds;
    @Autowired
    NoticeStore store;
    @Autowired
    NoticeRepository notices;
    @Autowired
    DateItemRepository dateItems;
    @Autowired
    AppUserRepository users;
    @Autowired
    DigestService digest;
    @Autowired
    ImageNoticeReader imageReader;
    @Autowired
    FakeTranscriber transcriber;
    @Autowired
    FakeAudienceClassifier audienceClassifier;
    @Autowired
    com.cukalimi.notice.AudienceReader audienceReader;
    @Autowired
    ImageTranscriptRepository transcripts;
    @Autowired
    ReviewEventRepository reviewEvents;

    /** 1로 표시한 그림에는 기간 한 줄을, 나머지에는 빈 글을 돌려준다. 몇 번 불렸는지 센다. */
    static class FakeTranscriber implements ImageTranscriber {
        int calls;
        /** 참이면 읽을 때마다 날짜가 달라져 두 번 읽기가 어긋난다. */
        boolean disagree = true;

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Result transcribe(byte[] image, String mediaType) {
            calls++;
            int day = disagree && calls % 2 == 0 ? 30 : 31;
            String text = image[0] == 1 ? "<p>신청기간</p><p>2026. 7. 20.(월) ~ 7. " + day + ".(금)</p>" : "";
            return new Result(text, "fake", 10, 5);
        }
    }

    /** 제목에 "AI 대상"이 있으면 3학년 컴퓨터정보공학부 조건을, "어긋남"이 있으면 읽을 때마다 다른 학년을 돌려준다. */
    static class FakeAudienceClassifier implements com.cukalimi.notice.AudienceClassifier {
        int calls;

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Result classify(String title, String text) {
            calls++;
            List<Integer> grades = title.contains("AI 대상") ? List.of(3) : title.contains("어긋남") ? List.of(calls % 2 + 1) : List.of();
            List<String> depts = title.contains("AI 대상") ? List.of("컴퓨터정보공학부") : List.of();
            return new Result(new com.cukalimi.feed.AudienceFacts(grades, depts, List.of(), List.of(), ""), "fake", 100, 20);
        }
    }

    @Test
    void feedFromIncludesPastDeadlinesOnlyWhenAsked() {
        // 고정한 오늘(7/10)보다 앞선 마감. 월 보기가 지난달을 그릴 때만 들어온다.
        long noticeId = notices.findByArticleNo(273306L).orElseThrow().getId();
        long pastId = dateItems.save(DateItem.manual(noticeId, "지난 신청", null, LocalDate.of(2026, 7, 1), null, null,
                "ALL", "ALL", null, "test")).getId();
        String marker = "\"id\":" + pastId + ",";
        assertThat(rest.getForObject("/api/feed", String.class)).doesNotContain(marker);
        assertThat(rest.getForObject("/api/feed?from=2026-06-01", String.class)).contains(marker);
        // 오늘보다 늦은 from은 오늘로, 아주 이른 from은 작년 1월 1일로 자른다.
        assertThat(rest.getForObject("/api/feed?from=2026-12-01", String.class)).doesNotContain(marker);
        assertThat(rest.getForObject("/api/feed?from=2000-01-01", String.class)).contains(marker);
        dateItems.deleteById(pastId);
    }

    @Autowired
    com.cukalimi.notice.NoticeCollector noticeCollector;

    @Autowired
    com.cukalimi.ops.HealthAlert healthAlert;

    @Test
    void healthAlertNamesAStoppedCollector() {
        // 이 테스트 서버는 학교 사이트에 요청하지 않으니 "요청 0건"이 문제로 잡힌다. 공지는 방금 들어왔으니 조용하지 않다.
        assertThat(healthAlert.problems()).anyMatch(p -> p.contains("한 번도 요청하지 않았어요"))
                .noneMatch(p -> p.contains("새 공지가 하나도"));
    }

    @Test
    void rowInsertedIntoTableKeepsSavedItemOnItsOwnDeadline() {
        String cookie = login("insert@catholic.ac.kr");
        String html = Fixtures.read("notice-273306.html").replace("273306", "999501").replace("수강신청 일정 안내", "수강신청 끼움 시험");
        store.save(new NoticeSummary(999501L, "학사", "", LocalDate.of(2026, 6, 29)), html);
        Notice n = notices.findByArticleNo(999501L).orElseThrow();
        try {
            DateItem kept = dateItems.findByNoticeIdOrderByEndsOnAsc(n.getId()).stream()
                    .filter(i -> i.getTitle().contains("수강바구니")).findFirst().orElseThrow();
            rest.exchange("/api/me/saved/" + kept.getId(), HttpMethod.POST, withCookie(cookie, null), Void.class);
            // 학교가 표 맨 위에 행을 하나 끼워 넣는다. 자리가 한 칸씩 밀려도 저장한 항목은 같은 마감에 붙어 있어야 한다.
            int first = html.indexOf("<tr", html.indexOf("<tbody"));
            String inserted = html.substring(0, first) + "<tr><td>새로 생긴 신청</td><td>2026.07.20.(월) ~ 07.21.(화)</td></tr>" + html.substring(first);
            store.save(new NoticeSummary(999501L, "학사", "", LocalDate.of(2026, 6, 29)), inserted);
            DateItem after = dateItems.findById(kept.getId()).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(DateItem.STATUS_AUTO);
            assertThat(after.getTitle()).isEqualTo(kept.getTitle());
            assertThat(after.getEndsOn()).isEqualTo(kept.getEndsOn());
            assertThat(dateItems.findByNoticeIdOrderByEndsOnAsc(n.getId())).anyMatch(i -> i.getTitle().contains("새로 생긴 신청"));
        } finally {
            rest.exchange("/api/me", HttpMethod.DELETE, withCookie(cookie, null), Void.class);
            dateItems.deleteAll(dateItems.findByNoticeIdOrderByEndsOnAsc(n.getId()));
            notices.delete(n);
        }
    }

    @Test
    void reparseKeepsSavedItemsAndPushTreatsSameDayLinesAsOne() {
        String cookie = login("keep@catholic.ac.kr");
        AppUser user = users.findByEmail("keep@catholic.ac.kr").orElseThrow();
        String html = Fixtures.read("notice-273306.html").replace("273306", "999401").replace("수강신청 일정 안내", "수강신청 보존 시험");
        store.save(new NoticeSummary(999401L, "학사", "", LocalDate.of(2026, 6, 29)), html);
        Notice n = notices.findByArticleNo(999401L).orElseThrow();
        try {
            DateItem kept = dateItems.findByNoticeIdOrderByEndsOnAsc(n.getId()).stream()
                    .filter(i -> i.getTitle().contains("수강바구니")).findFirst().orElseThrow();
            rest.exchange("/api/me/saved/" + kept.getId(), HttpMethod.POST, withCookie(cookie, null), Void.class);

            // 표의 라벨이 바뀌어 같은 자리에 다른 일이 오면, 저장한 항목은 덮어쓰지 않고 거둔다. 저장은 남는다.
            store.save(new NoticeSummary(999401L, "학사", "", LocalDate.of(2026, 6, 29)), html.replace("수강바구니", "전혀다른일"));
            DateItem retired = dateItems.findById(kept.getId()).orElseThrow();
            assertThat(retired.getStatus()).isEqualTo(DateItem.STATUS_RETIRED);
            assertThat(retired.getEndsOn()).isEqualTo(kept.getEndsOn());
            assertThat(rest.exchange("/api/feed", HttpMethod.GET, withCookie(cookie, null), String.class).getBody())
                    .doesNotContain("\"id\":" + kept.getId() + ",").contains("전혀다른일");

        // 같은 날 줄 둘(홈에서 한 행)은 푸시 하나이고, 하나라도 끝냈으면 보내지 않는다. 고정한 오늘은 7/10이라 D-3은 7/13이다.
        long a = dateItems.save(DateItem.manual(n.getId(), "건국대학교 신청", null, LocalDate.of(2026, 7, 13), null, null, "ALL", "ALL", null, "t")).getId();
        long b = dateItems.save(DateItem.manual(n.getId(), "상명대학교 신청", null, LocalDate.of(2026, 7, 13), null, null, "ALL", "ALL", null, "t")).getId();
        rest.exchange("/api/me/saved/" + a, HttpMethod.POST, withCookie(cookie, null), Void.class);
        rest.exchange("/api/me/saved/" + b, HttpMethod.POST, withCookie(cookie, null), Void.class);
        assertThat(pushService.remindersFor(user, LocalDate.of(2026, 7, 10))).hasSize(1);
        rest.exchange("/api/me/done/" + a, HttpMethod.POST, withCookie(cookie, null), Void.class);
        assertThat(pushService.remindersFor(user, LocalDate.of(2026, 7, 10))).isEmpty();
        } finally {
            // 다른 테스트가 세는 카드 수에 끼지 않게 치운다.
            rest.exchange("/api/me", HttpMethod.DELETE, withCookie(cookie, null), Void.class);
            dateItems.deleteAll(dateItems.findByNoticeIdOrderByEndsOnAsc(n.getId()));
            notices.delete(n);
        }
    }

    @Test
    void repostedAndDeletedNoticesLeaveTheFeed() {
        // 제목을 바꿔 둔다. 같은 제목이면 같이 심은 273306까지 묵혀 다른 테스트가 흔들린다.
        String html = Fixtures.read("notice-273306.html").replace("수강신청 일정 안내", "수강신청 일정 재공지");
        // 학교가 같은 공지를 고쳐 다시 올리면 같은 날 같은 제목의 글이 둘이다. 번호 작은 쪽을 묵힌다.
        store.save(new NoticeSummary(999301L, "학사", "", LocalDate.of(2026, 6, 29)), html.replace("273306", "999301"));
        store.save(new NoticeSummary(999302L, "학사", "", LocalDate.of(2026, 6, 29)), html.replace("273306", "999302"));
        Notice older = notices.findByArticleNo(999301L).orElseThrow();
        Notice newer = notices.findByArticleNo(999302L).orElseThrow();
        assertThat(older.isRemoved()).isTrue();
        assertThat(older.getRemovedReason()).isEqualTo(Notice.REMOVED_DUPLICATE);
        assertThat(newer.isRemoved()).isFalse();
        String feed = rest.getForObject("/api/feed", String.class);
        long olderItem = dateItems.findByNoticeIdOrderByEndsOnAsc(older.getId()).get(0).getId();
        long newerItem = dateItems.findByNoticeIdOrderByEndsOnAsc(newer.getId()).get(0).getId();
        assertThat(feed).contains("\"id\":" + newerItem + ",").doesNotContain("\"id\":" + olderItem + ",");

        // 학교가 지운 글은 하루 한 번 재확인에서 404로 드러난다. 점검 중의 404 하루로 사라지지 않게 두 번째에 내리고, 다시 열리면 되살린다.
        store.save(new NoticeSummary(999303L, "학사", "", LocalDate.of(2026, 6, 30)), html.replace("273306", "999303"));
        Notice gone = notices.findByArticleNo(999303L).orElseThrow();
        long goneItem = dateItems.findByNoticeIdOrderByEndsOnAsc(gone.getId()).get(0).getId();
        assertThat(rest.getForObject("/api/feed", String.class)).contains("\"id\":" + goneItem + ",");
        noticeCollector.recheckOpenNotices(LocalDate.of(2026, 6, 1));
        assertThat(notices.findByArticleNo(999303L).orElseThrow().isRemoved()).as("404 한 번은 지켜본다").isFalse();
        noticeCollector.recheckOpenNotices(LocalDate.of(2026, 6, 1));
        Notice after = notices.findByArticleNo(999303L).orElseThrow();
        assertThat(after.isRemoved()).isTrue();
        assertThat(after.getRemovedReason()).isEqualTo(Notice.REMOVED_GONE);
        assertThat(rest.getForObject("/api/feed", String.class)).doesNotContain("\"id\":" + goneItem + ",");
        ALIVE_999303.set(true);
        try {
            noticeCollector.recheckOpenNotices(LocalDate.of(2026, 6, 1));
        } finally {
            ALIVE_999303.set(false);
        }
        after = notices.findByArticleNo(999303L).orElseThrow();
        assertThat(after.isRemoved()).as("다시 열리면 되살린다").isFalse();
        assertThat(rest.getForObject("/api/feed", String.class)).contains("\"id\":" + goneItem + ",");
        // 나머지 테스트가 세는 공지 수에 영향을 주지 않게 치운다.
        for (Notice n : List.of(older, newer, after)) {
            dateItems.deleteAll(dateItems.findByNoticeIdOrderByEndsOnAsc(n.getId()));
            notices.delete(n);
        }
    }

    /** 푸시 서비스 대신 보낸 내용을 모은다. gone을 켜면 구독이 사라진 것처럼 군다. */
    static class FakePushSender implements com.cukalimi.push.PushService.Sender {
        final List<String> payloads = new java.util.ArrayList<>();
        boolean gone;

        @Override
        public com.cukalimi.push.WebPush.Outcome send(com.cukalimi.push.WebPush.Subscription sub, String payloadJson) {
            if (gone) {
                return com.cukalimi.push.WebPush.Outcome.GONE;
            }
            payloads.add(sub.endpoint() + " " + payloadJson);
            return com.cukalimi.push.WebPush.Outcome.SENT;
        }

        @Override
        public String publicKey() {
            return "BFAKEKEY";
        }

        @Override
        public boolean enabled() {
            return true;
        }
    }

    @Autowired
    FakePushSender pushSender;
    @Autowired
    com.cukalimi.push.PushService pushService;
    @Autowired
    com.cukalimi.domain.PushSubscriptionRepository pushSubscriptions;

    @Test
    void pushGoesAtDMinus3OncePerDeviceAndGoneSubscriptionsAreDropped() {
        String cookie = login("push@catholic.ac.kr");
        AppUser user = users.findByEmail("push@catholic.ac.kr").orElseThrow();
        // 공개 키는 로그인 없이 읽는다. 등록 기기 수는 로그인해야 센다.
        ResponseEntity<Map> state = rest.getForEntity("/api/push", Map.class);
        assertThat(state.getBody().get("enabled")).isEqualTo(true);
        assertThat(state.getBody().get("publicKey")).isEqualTo("BFAKEKEY");

        Map<String, Object> sub = Map.of("endpoint", "https://fcm.googleapis.com/fcm/send/dev-1", "keys", Map.of("p256dh", "BPUB", "auth", "AUTH"));
        assertThat(rest.exchange("/api/me/push", HttpMethod.POST, withCookie(cookie, sub), Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.postForEntity("/api/me/push", sub, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // 푸시 서비스가 아닌 주소는 받지 않는다. 서버가 남의 주소로 요청을 보내게 될 수 있다.
        for (String bad : List.of("http://fcm.googleapis.com/x", "https://evil.example/x", "https://fcm.googleapis.com:8443/x")) {
            assertThat(rest.exchange("/api/me/push", HttpMethod.POST,
                    withCookie(cookie, Map.of("endpoint", bad, "keys", Map.of("p256dh", "B", "auth", "A"))), String.class)
                    .getStatusCode()).as(bad).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(((Number) rest.exchange("/api/push", HttpMethod.GET, withCookie(cookie, null), Map.class).getBody().get("devices"))
                .longValue()).isEqualTo(1L);

        // 고정한 오늘(7/10)에서 D-3인 항목을 넣고 학생이 저장한다. 푸시는 저장한 줄과 내게 맞는 줄만 본다.
        long noticeId = notices.findByArticleNo(273306L).orElseThrow().getId();
        long itemId = dateItems.save(DateItem.manual(noticeId, "푸시 확인 신청", null, LocalDate.of(2026, 7, 13), null,
                java.time.LocalTime.of(17, 0), "ALL", "ALL", null, "test")).getId();
        rest.exchange("/api/me/saved/" + itemId, HttpMethod.POST, withCookie(cookie, null), Void.class);

        // 푸시 서비스가 410을 주면 구독을 지우고, 못 보냈으니 키도 남기지 않는다.
        pushSender.gone = true;
        assertThat(pushService.sendTo(user).gone()).isEqualTo(1);
        assertThat(pushSubscriptions.findByUserId(user.getId())).isEmpty();
        pushSender.gone = false;
        rest.exchange("/api/me/push", HttpMethod.POST, withCookie(cookie, sub), Void.class);

        assertThat(pushService.sendTo(user).sent()).isEqualTo(1);
        assertThat(pushSender.payloads).hasSize(1);
        assertThat(pushSender.payloads.get(0)).startsWith("https://fcm.googleapis.com/fcm/send/dev-1 ").contains("3일 뒤 마감").contains("\"url\"");
        // 같은 사건은 두 번 가지 않는다.
        assertThat(pushService.sendTo(user).sent()).isEqualTo(0);

        // 끝냈어요로 체크한 줄은 D-1이 와도 보내지 않는다 (여기서는 키가 이미 남아 있어 D-3도 다시 가지 않는다).
        rest.exchange("/api/me/done/" + itemId, HttpMethod.POST, withCookie(cookie, null), Void.class);
        assertThat(pushService.remindersFor(user, LocalDate.of(2026, 7, 12))).isEmpty();
        rest.exchange("/api/me/done/" + itemId, HttpMethod.DELETE, withCookie(cookie, null), Void.class);
        assertThat(pushService.remindersFor(user, LocalDate.of(2026, 7, 12))).hasSize(1);

        // 이 기기만 끈다. 탈퇴하면 구독도 같이 지워진다.
        assertThat(rest.exchange("/api/me/push", HttpMethod.DELETE, withCookie(cookie, Map.of("endpoint", "https://fcm.googleapis.com/fcm/send/dev-1")),
                Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(pushSubscriptions.findByUserId(user.getId())).isEmpty();
        rest.exchange("/api/me/push", HttpMethod.POST, withCookie(cookie, sub), Void.class);
        rest.exchange("/api/me", HttpMethod.DELETE, withCookie(cookie, null), Void.class);
        assertThat(pushSubscriptions.findByEndpoint("https://fcm.googleapis.com/fcm/send/dev-1")).isEmpty();
    }

    @BeforeEach
    void seedNotices() {
        // consume는 화면 주소로 302를 보낸다. 테스트 클라이언트가 따라가면 안 된다.
        rest.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NEVER).build()));
        if (notices.count() == 0) {
            store.save(new NoticeSummary(273306L, "학사", "", LocalDate.of(2026, 6, 29)), Fixtures.read("notice-273306.html"));
            store.save(new NoticeSummary(274128L, "학사", "", LocalDate.of(2026, 7, 27)), Fixtures.read("notice-274128.html"));
        }
    }

    @Test
    void anyoneCanReadItemsAndBenefits() {
        ResponseEntity<List> items = rest.getForEntity("/api/items?includePast=true", List.class);
        assertThat(items.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(items.getBody()).hasSizeGreaterThanOrEqualTo(8);

        // 품질 수치는 로그인 없이 공개한다.
        Map quality = rest.getForObject("/api/quality", Map.class);
        assertThat(((Number) ((Map) ((Map) quality.get("backtest")).get("all")).get("precision")).doubleValue()).isGreaterThan(0.9);
        assertThat(quality.get("review")).isNotNull();
        assertThat(quality.get("transcription")).isNotNull();

        ResponseEntity<List> benefits = rest.getForEntity("/api/benefits", List.class);
        assertThat(benefits.getBody()).hasSizeGreaterThanOrEqualTo(15);

        assertThat(rest.getForEntity("/api/me", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.getForEntity("/api/admin/review", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void feedPutsRegistrationFirstAsOneCard() {
        Map feed = rest.getForObject("/api/feed", Map.class);
        List<Map> sections = (List<Map>) feed.get("sections");
        Map must = sections.get(0);
        assertThat(must.get("key")).isEqualTo("must");
        List<Map> cards = (List<Map>) must.get("cards");
        // 수강신청 일정 공지는 날짜가 여러 개지만 카드는 한 장이다.
        assertThat(cards).filteredOn(c -> "notice".equals(((String) c.get("key")).split(":")[0])).hasSize(1);
        Map card = cards.stream().filter(c -> ((String) c.get("key")).startsWith("notice:")).findFirst().orElseThrow();
        assertThat((List) card.get("next")).isNotEmpty();
        assertThat(feed.get("signedIn")).isEqualTo(false);

        List<Map> regions = rest.getForObject("/api/regions", List.class);
        assertThat(regions).hasSize(17);
    }

    @Test
    void magicLinkLogsInOnceAndSessionWorks() {
        String cookie = login("student@catholic.ac.kr");

        ResponseEntity<Map> me = rest.exchange("/api/me", HttpMethod.GET, withCookie(cookie, null), Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("email")).isEqualTo("student@catholic.ac.kr");
        assertThat(me.getBody().get("admin")).isEqualTo(false);

        // 같은 링크를 또 쓰면 안 된다.
        String link = lastLink();
        ResponseEntity<Void> again = rest.getForEntity(URI.create(link), Void.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 프로필을 넣으면 학년이 계산된다. 연고 지역은 목록에 있는 값만 받는다.
        ResponseEntity<Map> profile = rest.exchange("/api/me/profile", HttpMethod.PUT,
                withCookie(cookie, Map.of("entryYear", 2023, "major", "컴퓨터정보공학부", "homeRegion", "전라남도 순천시")), Map.class);
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profile.getBody().get("grade")).isNotNull();
        assertThat(profile.getBody().get("homeRegion")).isEqualTo("전라남도 순천시");
        assertThat(rest.exchange("/api/me/profile", HttpMethod.PUT,
                withCookie(cookie, Map.of("homeRegion", "전라남도 강남구")), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // 휴학으로 늦어진 학기는 직접 고른다. 학년은 학기에서 나온다. 입학년도 없이는 고를 수 없다.
        ResponseEntity<Map> manual = rest.exchange("/api/me/profile", HttpMethod.PUT,
                withCookie(cookie, Map.of("entryYear", 2010, "semester", 6)), Map.class);
        assertThat(manual.getBody().get("semester")).isEqualTo(6);
        assertThat(manual.getBody().get("grade")).isEqualTo(3);
        assertThat(manual.getBody().get("autoSemester")).isEqualTo(9);
        assertThat(manual.getBody().get("semesterManual")).isEqualTo(true);
        assertThat(rest.exchange("/api/me/profile", HttpMethod.PUT,
                withCookie(cookie, Map.of("entryYear", 2021, "semester", 10)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.exchange("/api/me/profile", HttpMethod.PUT,
                withCookie(cookie, Map.of("semester", 3)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // 관심 주제. 처음에는 기본 다섯 개이고, 고르면 그대로 남고, 모르는 키는 받지 않는다 (ADR 014).
        ResponseEntity<Map> plain = rest.exchange("/api/me", HttpMethod.GET, withCookie(cookie, null), Map.class);
        assertThat((List<String>) plain.getBody().get("interests")).containsExactlyInAnyOrder("graduation", "seasonal", "workstudy", "course", "scholarship");
        ResponseEntity<Map> picked = rest.exchange("/api/me/profile", HttpMethod.PUT,
                withCookie(cookie, Map.of("entryYear", 2010, "semester", 6, "interests", List.of("course", "exchange"))), Map.class);
        assertThat((List<String>) picked.getBody().get("interests")).containsExactly("exchange", "course");
        assertThat(rest.exchange("/api/me/profile", HttpMethod.PUT,
                withCookie(cookie, Map.of("entryYear", 2010, "interests", List.of("nope"))), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        // interests를 안 보내면 바꾸지 않는다.
        ResponseEntity<Map> kept = rest.exchange("/api/me/profile", HttpMethod.PUT, withCookie(cookie, Map.of("entryYear", 2010, "semester", 6)), Map.class);
        assertThat((List<String>) kept.getBody().get("interests")).containsExactly("exchange", "course");

        // 항목 저장과 혜택 체크
        long itemId = ((Number) ((Map) rest.getForObject("/api/items?includePast=true", List.class).get(0)).get("id")).longValue();
        assertThat(rest.exchange("/api/me/saved/" + itemId, HttpMethod.POST, withCookie(cookie, null), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 끝냈어요 체크는 서버에 남고, 피드의 줄에 done으로 돌아온다. 로그인 없이는 못 한다.
        assertThat(rest.exchange("/api/me/done/" + itemId, HttpMethod.POST, withCookie(cookie, null), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.exchange("/api/feed", HttpMethod.GET, withCookie(cookie, null), String.class).getBody())
                .contains("\"id\":" + itemId + ",").contains("\"done\":true");
        assertThat(rest.exchange("/api/me/done/" + itemId, HttpMethod.DELETE, withCookie(cookie, null), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.exchange("/api/feed", HttpMethod.GET, withCookie(cookie, null), String.class).getBody())
                .doesNotContain("\"done\":true");
        assertThat(rest.postForEntity("/api/me/done/" + itemId, null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        List<Map> items = rest.exchange("/api/items?includePast=true", HttpMethod.GET, withCookie(cookie, null), List.class).getBody();
        assertThat(items.stream().filter(i -> ((Number) i.get("id")).longValue() == itemId).findFirst().orElseThrow().get("saved"))
                .isEqualTo(true);

        // 관리자 아님
        assertThat(rest.exchange("/api/admin/review", HttpMethod.GET, withCookie(cookie, null), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 로그아웃 뒤에는 세션이 죽는다.
        rest.exchange("/api/auth/logout", HttpMethod.POST, withCookie(cookie, null), Void.class);
        assertThat(rest.exchange("/api/me", HttpMethod.GET, withCookie(cookie, null), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminReviewsAndAddsManualItem() {
        String cookie = login("admin@catholic.ac.kr");

        Map review = rest.exchange("/api/admin/review", HttpMethod.GET, withCookie(cookie, null), Map.class).getBody();
        List<Map> queue = (List<Map>) review.get("notices");
        assertThat(queue).hasSizeGreaterThanOrEqualTo(2);
        // 수강신청 공지가 맨 앞이다. 모든 재학생이 대상이라서.
        assertThat(queue.get(0).get("priority")).isEqualTo(0);
        Map imageOnly = queue.stream().filter(n -> ((Number) n.get("articleNo")).longValue() == 274128L).findFirst().orElseThrow();
        assertThat(imageOnly.get("reviewReason")).isEqualTo(Notice.REASON_IMAGE_ONLY);
        long noticeId = ((Number) imageOnly.get("id")).longValue();
        // 날짜를 넣기 전에는 홈의 접힌 "날짜 확인 중"에 날짜 없이 있다.
        assertThat(undatedKeys()).contains("notice:" + noticeId);
        assertThat(rest.exchange("/api/admin/feedback", HttpMethod.GET, withCookie(cookie, null), List.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // 그림만 있는 공지에 날짜를 손으로 넣는다.
        ResponseEntity<Map> added = rest.exchange("/api/admin/notices/" + noticeId + "/items", HttpMethod.POST,
                withCookie(cookie, Map.of("title", "제1전공생 우선 수강신청", "startsOn", "2026-07-30", "endsOn", "2026-07-31",
                        "endsAt", "17:00", "targetGrade", "ALL", "targetDept", "ALL")), Map.class);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(added.getBody().get("sourceKind")).isEqualTo(DateItem.SOURCE_MANUAL);
        assertThat(added.getBody().get("reviewed")).isEqualTo(true);
        assertThat(undatedKeys()).doesNotContain("notice:" + noticeId);

        // 마감 없는 항목은 거부
        ResponseEntity<String> bad = rest.exchange("/api/admin/notices/" + noticeId + "/items", HttpMethod.POST,
                withCookie(cookie, Map.of("title", "x")), String.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 대상 범위를 운영자가 고친다. 잘못된 값은 거부.
        ResponseEntity<Map> audience = rest.exchange("/api/admin/notices/" + noticeId + "/audience", HttpMethod.PUT,
                withCookie(cookie, Map.of("scope", "GROUP", "label", "제1전공생")), Map.class);
        assertThat(audience.getBody().get("audienceScope")).isEqualTo("GROUP");
        assertThat(audience.getBody().get("audienceOverridden")).isEqualTo(true);
        assertThat(rest.exchange("/api/admin/notices/" + noticeId + "/audience", HttpMethod.PUT,
                withCookie(cookie, Map.of("scope", "ANYONE")), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 공지를 검수 완료로
        ResponseEntity<Map> done = rest.exchange("/api/admin/notices/" + noticeId + "/reviewed", HttpMethod.POST,
                withCookie(cookie, null), Map.class);
        assertThat(done.getBody().get("status")).isEqualTo(Notice.STATUS_REVIEWED);
        assertThat(notices.findById(noticeId).orElseThrow().getStatus()).isEqualTo(Notice.STATUS_REVIEWED);

        // 수강신청 공지의 자동 항목 하나를 고치고 태그를 붙인다.
        Map registration = queue.stream().filter(n -> ((Number) n.get("articleNo")).longValue() == 273306L).findFirst().orElseThrow();
        Map firstItem = (Map) ((List) registration.get("items")).get(0);
        long itemId = ((Number) firstItem.get("id")).longValue();
        ResponseEntity<Map> edited = rest.exchange("/api/admin/items/" + itemId, HttpMethod.PUT,
                withCookie(cookie, Map.of("title", "수강바구니 (재학생)", "startsOn", "2026-07-07", "endsOn", "2026-07-12",
                        "targetGrade", "ALL", "targetDept", "ALL")), Map.class);
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody().get("reviewed")).isEqualTo(true);
        assertThat(edited.getBody().get("bucket")).isEqualTo(1);
        DateItem stored = dateItems.findById(itemId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(DateItem.STATUS_REVIEWED);
        assertThat(stored.getTitle()).isEqualTo("수강바구니 (재학생)");

        // 지운 항목도 기록이 남는다.
        long manualId = ((Number) added.getBody().get("id")).longValue();
        rest.exchange("/api/admin/items/" + manualId, HttpMethod.DELETE, withCookie(cookie, null), Void.class);

        // 검수 동작은 모두 고치기 전후 값과 함께 남는다. 정확도를 세는 재료다.
        List<ReviewEvent> imageEvents = reviewEvents.findByNoticeIdOrderByIdAsc(noticeId);
        assertThat(imageEvents).extracting(ReviewEvent::getAction)
                .containsExactly(ReviewEvent.ADD, ReviewEvent.APPROVE, ReviewEvent.DELETE);
        assertThat(imageEvents.get(0).getAfterJson()).contains("\"endsOn\":\"2026-07-31\"");
        assertThat(imageEvents.get(1).getDateItemId()).isNull();
        assertThat(imageEvents.get(2).getBeforeJson()).contains("제1전공생 우선 수강신청");
        long registrationId = ((Number) registration.get("id")).longValue();
        ReviewEvent edit = reviewEvents.findByNoticeIdOrderByIdAsc(registrationId).get(0);
        assertThat(edit.getAction()).isEqualTo(ReviewEvent.EDIT);
        assertThat(edit.getBeforeJson()).contains("\"status\":\"AUTO\"");
        assertThat(edit.getAfterJson()).contains("수강바구니 (재학생)");
    }

    @Test
    void imageNoticeGetsCandidateDatesThatWaitForReview() {
        // 다른 테스트가 274128에 손으로 날짜를 넣으므로 번호, 그림 주소, 제목을 바꾼 그림 공지를 따로 넣는다.
        // 제목에 수강신청이 있으면 홈 맨 앞 구간에 들어가 다른 테스트의 기대가 깨진다.
        store.save(new NoticeSummary(999001L, "장학", "", LocalDate.of(2026, 7, 27)),
                Fixtures.read("notice-274128.html").replace("274128", "999001").replace("editor-image/", "editor-image/copy-")
                        .replace("[학사지원팀] 2026-2학기 제1전공생 우선 수강신청 시행 안내", "[학생지원팀] 테스트 장학생 선발 안내"));
        long noticeId = notices.findByArticleNo(999001L).orElseThrow().getId();

        imageReader.readPending(LocalDate.of(2026, 7, 10));
        assertThat(transcripts.findAll()).anyMatch(t -> t.getArticleNo() == 999001L);
        List<DateItem> items = dateItems.findByNoticeIdOrderByEndsOnAsc(noticeId);
        assertThat(items).singleElement().satisfies(i -> {
            assertThat(i.getStatus()).isEqualTo(DateItem.STATUS_CANDIDATE);
            // "신청기간"은 자리 이름이라 공지 제목이 할 일이 된다.
            assertThat(i.getTitle()).isEqualTo("테스트 장학생 선발 안내");
            assertThat(i.getEndsOn()).isEqualTo(LocalDate.of(2026, 7, 31));
        });
        long itemId = items.get(0).getId();

        // 후보는 학생에게 나가지 않는다. 승인 전까지 "날짜 확인 중"에 남는다.
        assertThat(undatedKeys()).contains("notice:" + noticeId);
        // 후보가 모두 지나면 학생 화면에서는 내려가고 검수 큐에는 남는다.
        assertThat(feeds.build(null, LocalDate.of(2026, 8, 1)).view().sections()).noneMatch(section -> "undated".equals(section.key())
                && section.cards().stream().anyMatch(c -> ("notice:" + noticeId).equals(c.key())));
        assertThat(digest.sendReviewReminder()).isTrue();
        assertThat(mail.lastTo()).isEqualTo("admin@catholic.ac.kr");
        assertThat(mail.lastBody()).contains("http://localhost:3000/admin/");
        assertThat(itemIds()).doesNotContain(itemId);

        // 같은 그림은 다시 받아 적지 않는다.
        int calls = transcriber.calls;
        imageReader.readPending(LocalDate.of(2026, 7, 10));
        assertThat(transcriber.calls).isEqualTo(calls);

        // 학교가 공지를 고치면(기간 연장) 옛 후보를 지우고 다시 읽는다. 바뀌지 않은 그림은 캐시에서 읽어 비용이 없다.
        store.save(new NoticeSummary(999001L, "장학", "", LocalDate.of(2026, 7, 27)),
                Fixtures.read("notice-274128.html").replace("274128", "999001").replace("editor-image/", "editor-image/copy-")
                        .replace("[학사지원팀] 2026-2학기 제1전공생 우선 수강신청 시행 안내", "[학생지원팀] 테스트 장학생 선발 안내(기간 연장)"));
        assertThat(dateItems.findByNoticeIdOrderByEndsOnAsc(noticeId)).isEmpty();
        imageReader.readPending(LocalDate.of(2026, 7, 10));
        assertThat(transcriber.calls).isEqualTo(calls);
        items = dateItems.findByNoticeIdOrderByEndsOnAsc(noticeId);
        assertThat(items).singleElement().satisfies(i -> assertThat(i.getStatus()).isEqualTo(DateItem.STATUS_CANDIDATE));
        itemId = items.get(0).getId();

        // 그림 한 장이 사라져도(404) 나머지 그림의 날짜는 후보로 올라간다. 사라진 그림은 기록해 두고 다시 받지 않는다.
        store.save(new NoticeSummary(999002L, "장학", "", LocalDate.of(2026, 7, 27)),
                Fixtures.read("notice-274128.html").replace("274128", "999002")
                        .replace("editor-image/2026/07/kDetn", "editor-image/gone-kDetn")
                        .replace("editor-image/2026/07/IXli", "editor-image/copy2-IXli")
                        .replace("[학사지원팀] 2026-2학기 제1전공생 우선 수강신청 시행 안내", "[학생지원팀] 그림 하나가 사라진 장학 안내"));
        long brokenId = notices.findByArticleNo(999002L).orElseThrow().getId();
        imageReader.readPending(LocalDate.of(2026, 7, 10));
        assertThat(dateItems.findByNoticeIdOrderByEndsOnAsc(brokenId)).hasSize(1);
        assertThat(transcripts.findAll()).anyMatch(t -> t.getImageUrl().contains("/gone-") && t.getModel().startsWith("skipped"));

        // 검수 화면에서는 그림에서 읽은 후보로 보이고, 공지를 검수 완료로 두면 학생에게 나간다.
        String cookie = login("admin@catholic.ac.kr");
        Map one = rest.exchange("/api/admin/notices/" + noticeId, HttpMethod.GET, withCookie(cookie, null), Map.class).getBody();
        Map item = (Map) ((List) one.get("items")).get(0);
        assertThat(item.get("fromImage")).isEqualTo(true);
        assertThat(item.get("reviewed")).isEqualTo(false);
        rest.exchange("/api/admin/notices/" + noticeId + "/reviewed", HttpMethod.POST, withCookie(cookie, null), Map.class);
        assertThat(undatedKeys()).doesNotContain("notice:" + noticeId);
        assertThat(itemIds()).contains(itemId);
    }

    @Test
    void aiAudienceReadTwiceMarksMineWithReasonAndDisagreementFallsBack() {
        String html = Fixtures.read("notice-273306.html");
        store.save(new NoticeSummary(999101L, "학사", "", LocalDate.of(2026, 7, 1)), html.replace("273306", "999101")
                .replace("[학사지원팀] 2026학년도 2학기 수강신청 일정 안내", "[학사지원팀] AI 대상 수강 일정 안내"));
        store.save(new NoticeSummary(999102L, "학사", "", LocalDate.of(2026, 7, 1)), html.replace("273306", "999102")
                .replace("[학사지원팀] 2026학년도 2학기 수강신청 일정 안내", "[학사지원팀] 어긋남 일정 안내"));
        Notice agreedNotice = notices.findByArticleNo(999101L).orElseThrow();
        Notice disagreedNotice = notices.findByArticleNo(999102L).orElseThrow();

        audienceReader.readPending(LocalDate.of(2026, 7, 10));
        agreedNotice = notices.findById(agreedNotice.getId()).orElseThrow();
        disagreedNotice = notices.findById(disagreedNotice.getId()).orElseThrow();
        assertThat(agreedNotice.getAudienceAi()).contains("컴퓨터정보공학부");
        assertThat(agreedNotice.getAudienceModel()).isEqualTo("fake");
        // 두 번 읽은 학년이 다르면 저장하지 않는다. 읽은 시각은 남아 다시 부르지 않는다.
        assertThat(disagreedNotice.getAudienceAi()).isNull();
        assertThat(disagreedNotice.getAudienceModel()).isEqualTo("disagree:fake");
        assertThat(disagreedNotice.getAudienceReadAt()).isNotNull();

        int calls = audienceClassifier.calls;
        audienceReader.readPending(LocalDate.of(2026, 7, 10));
        assertThat(audienceClassifier.calls).isEqualTo(calls);

        // 프로필이 맞는 학생에게는 칠한 이유가 붙고, 학년이 다른 학생에게는 붙지 않는다.
        login("ai-third@catholic.ac.kr");
        AppUser third = users.findByEmail("ai-third@catholic.ac.kr").orElseThrow();
        third.updateProfile(2024, 5, "컴퓨터정보공학부", null, null, LocalDate.of(2026, 7, 10));
        users.save(third);
        String key = "notice:" + agreedNotice.getId();
        Feed.Card card = feeds.build(third, LocalDate.of(2026, 7, 10)).view().sections().stream()
                .flatMap(s -> s.cards().stream()).filter(c -> key.equals(c.key())).findFirst().orElseThrow();
        assertThat(card.mine()).isTrue();
        // 형광펜 이유는 주제와 자격을 함께 적는다 (ADR 014).
        assertThat(card.mineReason()).isEqualTo("수강·학적 · 3학년·컴퓨터정보공학부 대상");

        third.updateProfile(2024, 3, "컴퓨터정보공학부", null, null, LocalDate.of(2026, 7, 10));
        users.save(third);
        assertThat(feeds.build(third, LocalDate.of(2026, 7, 10)).view().sections().stream().flatMap(s -> s.cards().stream())
                .filter(c -> key.equals(c.key())).findFirst().orElseThrow().mine()).isFalse();

        // 학교가 공지를 고치면 대상도 다시 읽는다.
        store.save(new NoticeSummary(999101L, "학사", "", LocalDate.of(2026, 7, 1)), html.replace("273306", "999101")
                .replace("[학사지원팀] 2026학년도 2학기 수강신청 일정 안내", "[학사지원팀] AI 대상 수강 일정 안내(정정)"));
        assertThat(notices.findById(agreedNotice.getId()).orElseThrow().getAudienceReadAt()).isNull();
        audienceReader.readPending(LocalDate.of(2026, 7, 10));
        assertThat(audienceClassifier.calls).isEqualTo(calls + 2);
    }

    @Test
    void digestGoesOncePerEventAndStopsAfterUnsubscribeOrDelete() {
        String cookie = login("digest@catholic.ac.kr");
        AppUser user = users.findByEmail("digest@catholic.ac.kr").orElseThrow();
        // 픽스처 공지 둘은 방금 들어왔으니 "새 공지"다 (수강신청 공지가 must 구간). 저장한 마감은 3일 안이어야 한다.
        long itemId = dateItems.findAll().stream().filter(i -> i.getNoticeId() != null)
                .filter(i -> !i.getEndsOn().isBefore(LocalDate.of(2026, 7, 10)) && !i.getEndsOn().isAfter(LocalDate.of(2026, 7, 13)))
                .findFirst().orElseThrow().getId();
        rest.exchange("/api/me/saved/" + itemId, HttpMethod.POST, withCookie(cookie, null), Void.class);

        assertThat(digest.sendTo(user)).isTrue();
        assertThat(mail.lastTo()).isEqualTo("digest@catholic.ac.kr");
        assertThat(mail.lastBody()).contains("새로 올라온 공지").contains("저장한 마감").contains("/api/digest/unsubscribe?token=");
        // 같은 사건은 두 번 가지 않는다.
        assertThat(digest.sendTo(user)).isFalse();

        // 메일의 링크로 끈다. 로그인 없이.
        Matcher m = Pattern.compile("/api/digest/unsubscribe\\?token=[A-Za-z0-9]+").matcher(mail.lastBody());
        assertThat(m.find()).isTrue();
        ResponseEntity<Void> off = rest.getForEntity(URI.create(rest.getRootUri() + m.group()), Void.class);
        assertThat(off.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(off.getHeaders().getLocation().toString()).endsWith("/settings/?digest=off");
        assertThat(users.findById(user.getId()).orElseThrow().isDigestEnabled()).isFalse();

        // 설정에서 다시 켠다.
        ResponseEntity<Map> on = rest.exchange("/api/me/digest", HttpMethod.PUT, withCookie(cookie, Map.of("enabled", true)), Map.class);
        assertThat(on.getBody().get("digestEnabled")).isEqualTo(true);

        // 캘린더 구독 주소. 로그인 없이 토큰으로 읽고, 저장한 마감이 들어 있다. 다시 만들면 이전 주소는 404다.
        String token = (String) on.getBody().get("calendarToken");
        assertThat(token).hasSize(32);
        ResponseEntity<String> ics = rest.getForEntity("/api/calendar/" + token + ".ics", String.class);
        assertThat(ics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ics.getHeaders().getContentType().toString()).startsWith("text/calendar");
        assertThat(ics.getBody()).startsWith("BEGIN:VCALENDAR").contains("UID:item-" + itemId + "@cuk-alimi");
        ResponseEntity<Map> rotated = rest.exchange("/api/me/calendar/rotate", HttpMethod.POST, withCookie(cookie, null), Map.class);
        String newToken = (String) rotated.getBody().get("calendarToken");
        assertThat(newToken).hasSize(32).isNotEqualTo(token);
        assertThat(rest.getForEntity("/api/calendar/" + token + ".ics", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/calendar/" + newToken + ".ics", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // "이건 왜 없나요?" 한 줄. 빈 글은 받지 않는다. 남긴 글은 탈퇴할 때 같이 지운다(외래 키라 안 지우면 탈퇴가 실패한다).
        assertThat(rest.exchange("/api/feedback", HttpMethod.POST, withCookie(cookie, Map.of("message", "학과 공지도 보고 싶어요")),
                Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.exchange("/api/feedback", HttpMethod.POST, withCookie(cookie, Map.of("message", "  ")), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.postForEntity("/api/feedback", Map.of("message", "익명"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // 탈퇴하면 계정과 저장이 사라지고 세션도 죽는다.
        assertThat(rest.exchange("/api/me", HttpMethod.DELETE, withCookie(cookie, null), Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(users.findByEmail("digest@catholic.ac.kr")).isEmpty();
        assertThat(rest.exchange("/api/me", HttpMethod.GET, withCookie(cookie, null), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private List<Long> itemIds() {
        List<Map> items = rest.getForObject("/api/items?includePast=true", List.class);
        return items.stream().map(i -> ((Number) i.get("id")).longValue()).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> undatedKeys() {
        List<Map> sections = (List<Map>) rest.getForEntity("/api/feed", Map.class).getBody().get("sections");
        return sections.stream().filter(s -> "undated".equals(s.get("key")))
                .flatMap(s -> ((List<Map>) s.get("cards")).stream()).map(c -> (String) c.get("key")).toList();
    }

    @Test
    void loginMailStopsAtThreePerAddressPerHour() {
        String email = "limit@catholic.ac.kr";
        for (int i = 0; i < 3; i++) {
            assertThat(requestLink(email, "10.1.0.1").getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
        String lastBody = mail.lastBody();

        ResponseEntity<Map> fourth = requestLink(" LIMIT@catholic.ac.kr ", "10.1.0.1");

        assertThat(fourth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(mail.lastBody()).isEqualTo(lastBody);
    }

    @Test
    void newAddressesGetTheSameAnswerButNoMailPastTheDailyShare() {
        login("regular@catholic.ac.kr");
        List<LoginToken> flood = floodTokens(100);
        try {
            ResponseEntity<Map> skipped = requestLink("after-flood@catholic.ac.kr", "10.1.0.2");
            assertThat(skipped.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(mail.lastTo()).isNotEqualTo("after-flood@catholic.ac.kr");

            assertThat(requestLink("regular@catholic.ac.kr", "10.1.0.3").getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(mail.lastTo()).isEqualTo("regular@catholic.ac.kr");
        } finally {
            loginTokens.deleteAll(flood);
        }
    }

    @Test
    void everyoneStopsAtTheDailyCeiling() {
        login("ceiling@catholic.ac.kr");
        List<LoginToken> flood = floodTokens(300);
        try {
            assertThat(requestLink("ceiling@catholic.ac.kr", "10.1.0.6").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(requestLink("ceiling-new@catholic.ac.kr", "10.1.0.6").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        } finally {
            loginTokens.deleteAll(flood);
        }
    }

    private List<LoginToken> floodTokens(int count) {
        List<LoginToken> flood = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            flood.add(new LoginToken("flood" + i + "@catholic.ac.kr", "flood-hash-" + i, OffsetDateTime.now().plusMinutes(15)));
        }
        return loginTokens.saveAll(flood);
    }

    @Test
    void loginMailStopsAtTenPerIpPerHour() {
        for (int i = 0; i < 10; i++) {
            assertThat(requestLink("ip" + i + "@catholic.ac.kr", "10.1.0.4").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        assertThat(requestLink("ip10@catholic.ac.kr", "10.1.0.4").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(requestLink("ip10@catholic.ac.kr", "10.1.0.5").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void ipv6AddressesInOnePrefixShareTheIpLimit() {
        for (int i = 0; i < 10; i++) {
            assertThat(requestLink("six" + i + "@catholic.ac.kr", "2001:db8:1:2::" + (i + 1)).getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        assertThat(requestLink("six10@catholic.ac.kr", "2001:db8:1:2::ff").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void imageDatesThatReadTheSameTwiceGoPublicAndAReportTakesThemDown() {
        transcriber.disagree = false;
        try {
            store.save(new NoticeSummary(999101L, "장학", "", LocalDate.of(2026, 7, 8)),
                    Fixtures.read("notice-274128.html").replace("274128", "999101").replace("editor-image/", "editor-image/copy-twice-")
                            .replace("[학사지원팀] 2026-2학기 제1전공생 우선 수강신청 시행 안내", "[학생지원팀] 두 번 읽기 장학생 선발 안내"));
            imageReader.readPending(LocalDate.of(2026, 7, 10));
            long noticeId = notices.findByArticleNo(999101L).orElseThrow().getId();
            List<DateItem> items = dateItems.findByNoticeIdOrderByEndsOnAsc(noticeId);
            assertThat(items).singleElement().satisfies(i -> assertThat(i.getStatus()).isEqualTo(DateItem.STATUS_AUTO_IMAGE));
            long itemId = items.get(0).getId();
            assertThat(itemIds()).contains(itemId);
            assertThat(undatedKeys()).doesNotContain("notice:" + noticeId);

            assertThat(rest.postForEntity("/api/items/" + itemId + "/report", null, String.class).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            String cookie = login("reporter@catholic.ac.kr");
            assertThat(rest.exchange("/api/items/" + itemId + "/report", HttpMethod.POST, withCookie(cookie, null), Void.class)
                    .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            assertThat(itemIds()).doesNotContain(itemId);
            assertThat(undatedKeys()).contains("notice:" + noticeId);
            imageReader.readPending(LocalDate.of(2026, 7, 10));
            assertThat(dateItems.findById(itemId).orElseThrow().getStatus()).isEqualTo(DateItem.STATUS_CANDIDATE);

            // 학교가 공지를 고쳐도 신고로 내린 날짜는 사람이 볼 때까지 다시 공개되지 않는다.
            store.save(new NoticeSummary(999101L, "장학", "", LocalDate.of(2026, 7, 8)),
                    Fixtures.read("notice-274128.html").replace("274128", "999101").replace("editor-image/", "editor-image/copy-twice-")
                            .replace("[학사지원팀] 2026-2학기 제1전공생 우선 수강신청 시행 안내", "[학생지원팀] 두 번 읽기 장학생 선발 안내(수정)"));
            imageReader.readPending(LocalDate.of(2026, 7, 10));
            assertThat(dateItems.findById(itemId).orElseThrow().getStatus()).isEqualTo(DateItem.STATUS_CANDIDATE);

            // 신고한 사람이 탈퇴해도 내린 날짜는 다시 공개되지 않는다.
            assertThat(rest.exchange("/api/me", HttpMethod.DELETE, withCookie(cookie, null), Void.class).getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
            imageReader.readPending(LocalDate.of(2026, 7, 10));
            assertThat(dateItems.findById(itemId).orElseThrow().getStatus()).isEqualTo(DateItem.STATUS_CANDIDATE);

            // 운영자가 지운 날짜도 다시 읽어 올리지 않는다.
            String admin = login("admin@catholic.ac.kr");
            rest.exchange("/api/admin/items/" + itemId, HttpMethod.DELETE, withCookie(admin, null), Void.class);
            imageReader.readPending(LocalDate.of(2026, 7, 10));
            assertThat(dateItems.findByNoticeIdOrderByEndsOnAsc(noticeId)).isEmpty();
        } finally {
            transcriber.disagree = true;
        }
    }

    @Test
    void reportOnATextDateTellsTheOperatorAndKeepsTheDate() {
        DateItem item = dateItems.findByEndsOnGreaterThanEqualAndStatusNot(LocalDate.of(2026, 7, 10), DateItem.STATUS_CANDIDATE).stream()
                .filter(i -> DateItem.STATUS_AUTO.equals(i.getStatus())).findFirst().orElseThrow();
        String cookie = login("text-report@catholic.ac.kr");

        assertThat(rest.exchange("/api/items/" + item.getId() + "/report", HttpMethod.POST, withCookie(cookie, null), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(mail.lastTo()).isEqualTo("admin@catholic.ac.kr");
        assertThat(mail.lastBody()).contains("원문과 맞춰 봐 주세요");
        assertThat(itemIds()).contains(item.getId());
    }

    @Test
    void changedImageNoticeKeepsStudentSaves() {
        transcriber.disagree = false;
        try {
            String html = Fixtures.read("notice-274128.html").replace("274128", "999102").replace("editor-image/", "editor-image/copy-saved-")
                    .replace("[학사지원팀] 2026-2학기 제1전공생 우선 수강신청 시행 안내", "[학생지원팀] 저장 유지 장학생 선발 안내");
            NoticeSummary summary = new NoticeSummary(999102L, "장학", "", LocalDate.of(2026, 7, 8));
            store.save(summary, html);
            imageReader.readPending(LocalDate.of(2026, 7, 10));
            long noticeId = notices.findByArticleNo(999102L).orElseThrow().getId();
            long itemId = dateItems.findByNoticeIdOrderByEndsOnAsc(noticeId).get(0).getId();
            String cookie = login("saved-keeps@catholic.ac.kr");
            rest.exchange("/api/me/saved/" + itemId, HttpMethod.POST, withCookie(cookie, null), Void.class);

            store.save(summary, html.replace("저장 유지 장학생 선발 안내", "저장 유지 장학생 선발 안내(기간 연장)"));

            assertThat(dateItems.findById(itemId)).isPresent();
            assertThat(itemIds()).doesNotContain(itemId);
            imageReader.readPending(LocalDate.of(2026, 7, 10));
            assertThat(dateItems.findById(itemId).orElseThrow().getStatus()).isEqualTo(DateItem.STATUS_AUTO_IMAGE);
            assertThat(itemIds()).contains(itemId);
        } finally {
            transcriber.disagree = true;
        }
    }

    private ResponseEntity<Map> requestLink(String email, String forwardedFor) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        headers.add("X-Forwarded-For", forwardedFor);
        return rest.postForEntity("/api/auth/request", new HttpEntity<>(Map.of("email", email), headers), Map.class);
    }

    private String login(String email) {
        ResponseEntity<Map> requested = requestLink(email, "10.2.0." + Math.abs(email.hashCode() % 250));
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mail.lastTo()).isEqualTo(email);
        ResponseEntity<Void> consumed = rest.getForEntity(URI.create(lastLink()), Void.class);
        assertThat(consumed.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String setCookie = consumed.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("cuk_session=").contains("HttpOnly");
        return setCookie.split(";")[0];
    }

    /** 링크는 public-url로 만들어지니 테스트 서버 주소로 바꿔 부른다. */
    private String lastLink() {
        Matcher m = Pattern.compile("http://localhost:3000(/api/auth/consume\\?token=[A-Za-z0-9_-]+)").matcher(mail.lastBody());
        // api-url을 비워 두면 public-url로 링크가 만들어진다. 테스트 서버 주소로 바꿔 부른다.
        assertThat(m.find()).isTrue();
        return rest.getRootUri() + m.group(1);
    }

    private static HttpEntity<Object> withCookie(String cookie, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        if (body != null) {
            headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        }
        return new HttpEntity<>(body, headers);
    }
}
