package com.campusalimi;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusalimi.auth.ConsoleMailSender;
import com.campusalimi.digest.DigestService;
import com.campusalimi.domain.AppUser;
import com.campusalimi.domain.AppUserRepository;
import com.campusalimi.domain.DateItem;
import com.campusalimi.domain.DateItemRepository;
import com.campusalimi.domain.ImageTranscriptRepository;
import com.campusalimi.domain.LoginToken;
import com.campusalimi.domain.LoginTokenRepository;
import com.campusalimi.domain.ReviewEvent;
import com.campusalimi.domain.ReviewEventRepository;
import com.campusalimi.domain.Notice;
import com.campusalimi.domain.NoticeRepository;
import com.campusalimi.fetch.PageFetcher;
import com.campusalimi.notice.ImageNoticeReader;
import com.campusalimi.notice.ImageTranscriber;
import com.campusalimi.notice.NoticeStore;
import com.campusalimi.notice.NoticeSummary;
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

    @TestConfiguration
    static class NoNetwork {
        @Bean
        @Primary
        PageFetcher fetcher() {
            return new PageFetcher() {
                @Override
                public String get(String url) {
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
    com.campusalimi.feed.FeedService feeds;
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
    ImageTranscriptRepository transcripts;
    @Autowired
    ReviewEventRepository reviewEvents;

    /** 1로 표시한 그림에는 기간 한 줄을, 나머지에는 빈 글을 돌려준다. 몇 번 불렸는지 센다. */
    static class FakeTranscriber implements ImageTranscriber {
        int calls;

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Result transcribe(byte[] image, String mediaType) {
            calls++;
            String text = image[0] == 1 ? "<p>신청기간</p><p>2026. 7. 20.(월) ~ 7. 31.(금)</p>" : "";
            return new Result(text, "fake", 10, 5);
        }
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

        // 항목 저장과 혜택 체크
        long itemId = ((Number) ((Map) rest.getForObject("/api/items?includePast=true", List.class).get(0)).get("id")).longValue();
        assertThat(rest.exchange("/api/me/saved/" + itemId, HttpMethod.POST, withCookie(cookie, null), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
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
            assertThat(i.getTitle()).isEqualTo("신청기간");
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
        assertThat(setCookie).startsWith("ca_session=").contains("HttpOnly");
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
