package com.cukalimi;

import static org.assertj.core.api.Assertions.assertThat;

import com.cukalimi.benefit.BenefitSeedFile;
import com.cukalimi.benefit.BenefitStore;
import com.cukalimi.domain.BenefitRepository;
import com.cukalimi.domain.BenefitSeed;
import com.cukalimi.domain.DateItem;
import com.cukalimi.domain.DateItemRepository;
import com.cukalimi.domain.Notice;
import com.cukalimi.domain.NoticeRepository;
import com.cukalimi.fetch.PageFetcher;
import com.cukalimi.notice.NoticeCollector;
import com.cukalimi.notice.NoticeStore;
import com.cukalimi.notice.NoticeSummary;
import com.cukalimi.schedule.ScheduleCollector;
import com.cukalimi.schedule.ScheduleStore;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = { "app.scheduling.enabled=false", "app.fetch-interval-ms=0", "app.schedule.years=2026" })
class CollectorsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** 실제 학교 서버 대신 저장해 둔 페이지를 돌려준다. 목록은 본문 픽스처가 있는 글만 남긴다. 276772는 일부러 깨진 페이지를 준다. */
    @TestConfiguration
    static class FakeFetcherConfig {
        static final Set<String> KNOWN = Set.of("273306", "274128", "276772", "276997");
        static final String BROKEN = "276772";

        @Bean
        @Primary
        PageFetcher fakeFetcher() {
            return url -> {
                if (url.contains("calendar")) {
                    return Fixtures.read("calendar-2026.html");
                }
                if (url.contains("mode=view")) {
                    String no = url.replaceAll(".*articleNo=(\\d+).*", "$1");
                    if (!KNOWN.contains(no)) {
                        throw new IllegalStateException("unexpected fetch " + url);
                    }
                    return BROKEN.equals(no) ? "<html><body>점검 중</body></html>" : Fixtures.read("notice-" + no + ".html");
                }
                String fixture = url.contains("srCategoryId=22") ? "notice-list-scholarship.html" : "notice-list-academic.html";
                Document doc = Jsoup.parse(Fixtures.read(fixture));
                doc.select("tbody > tr").forEach(tr -> {
                    var a = tr.selectFirst("a.b-title[data-article-no]");
                    if (a == null || !KNOWN.contains(a.attr("data-article-no"))) {
                        tr.remove();
                    }
                });
                return doc.outerHtml();
            };
        }
    }

    @Autowired
    NoticeCollector noticeCollector;
    @Autowired
    NoticeStore noticeStore;
    @Autowired
    ScheduleCollector scheduleCollector;
    @Autowired
    NoticeRepository notices;
    @Autowired
    DateItemRepository dateItems;
    @Autowired
    BenefitStore benefitStore;
    @Autowired
    BenefitRepository benefitRepository;

    @Test
    void noticesAreStoredOnceAndOneBrokenPageDoesNotStopTheRun() {
        // 목록 첫 페이지 픽스처에는 최근 글 둘(276997 학사, 276772 장학)만 있다. 276772는 본문이 깨져 있다.
        NoticeCollector.Result first = noticeCollector.collectAll();
        assertThat(first.fetched()).isEqualTo(1);
        assertThat(first.failed()).isEqualTo(1);
        Notice broken = notices.findByArticleNo(276772L).orElseThrow();
        assertThat(broken.getReviewReason()).isEqualTo(Notice.REASON_PARSE_FAILED);
        assertThat(broken.getStatus()).isEqualTo(Notice.STATUS_NEEDS_REVIEW);

        Notice leave = notices.findByArticleNo(276997L).orElseThrow();
        List<DateItem> leaveItems = dateItems.findByNoticeIdOrderByEndsOnAsc(leave.getId());
        assertThat(leaveItems).hasSize(1);
        assertThat(leaveItems.get(0).getEndsAt()).isEqualTo(LocalTime.of(17, 30));
        assertThat(leaveItems.get(0).getStartsAt()).isNull();

        // 오래된 둘은 본문을 직접 넣는다.
        noticeStore.save(new NoticeSummary(273306L, "학사", "", LocalDate.of(2026, 6, 29)), Fixtures.read("notice-273306.html"));
        noticeStore.save(new NoticeSummary(274128L, "학사", "", LocalDate.of(2026, 7, 27)), Fixtures.read("notice-274128.html"));

        Notice registration = notices.findByArticleNo(273306L).orElseThrow();
        assertThat(registration.getReviewReason()).isEqualTo(Notice.REASON_PARSED);
        List<DateItem> items = dateItems.findByNoticeIdOrderByEndsOnAsc(registration.getId());
        assertThat(items).hasSize(9);
        assertThat(items).allSatisfy(i -> {
            assertThat(i.getSourceKind()).isEqualTo(DateItem.SOURCE_NOTICE);
            assertThat(i.getStatus()).isEqualTo(DateItem.STATUS_AUTO);
        });

        Notice priority = notices.findByArticleNo(274128L).orElseThrow();
        assertThat(priority.getReviewReason()).isEqualTo(Notice.REASON_IMAGE_ONLY);
        assertThat(dateItems.findByNoticeIdOrderByEndsOnAsc(priority.getId())).isEmpty();

        // 같은 글을 다시 처리해도 항목이 늘지 않는다.
        noticeStore.save(new NoticeSummary(273306L, "학사", "", LocalDate.of(2026, 6, 29)), Fixtures.read("notice-273306.html"));
        assertThat(dateItems.findByNoticeIdOrderByEndsOnAsc(registration.getId())).hasSize(9);

        NoticeCollector.Result second = noticeCollector.collectAll();
        assertThat(second.fetched()).isZero();
        assertThat(second.failed()).isZero();
        assertThat(notices.count()).isEqualTo(4);
    }

    @Test
    void scheduleIsIdempotentAndRefreshesChangedDates() {
        ScheduleStore.Result first = scheduleCollector.collect(2026);
        // 성심 항목 48건 중 돈·학적이 걸린 것만 남는다. 미사·교수회의·수업일수는 뺀다.
        assertThat(first.kept()).isBetween(15, 30);
        assertThat(dateItems.findByNaturalKey("schedule:2026:재학생 수강신청#1")).isPresent()
                .get().satisfies(i -> assertThat(i.getStatus()).isEqualTo(DateItem.STATUS_REVIEWED));
        assertThat(dateItems.findByNaturalKey("schedule:2026:교수회의#1")).isEmpty();
        // 교수가 성적을 내는 마감은 학생 일정이 아니다. 학생이 성적을 확인·정정하는 기간만 남긴다.
        assertThat(dateItems.findByNaturalKey("schedule:2026:2학기 성적제출 마감#1")).isEmpty();
        assertThat(dateItems.findByNaturalKey("schedule:2026:2학기 성적확인 및 오류정정#1")).isPresent();
        long stored = dateItems.countBySourceKind(DateItem.SOURCE_SCHEDULE);
        assertThat(stored).isEqualTo(first.kept());

        ScheduleStore.Result second = scheduleCollector.collect(2026);
        assertThat(second.inserted()).isZero();
        assertThat(second.updated()).isZero();
        assertThat(second.removed()).isZero();
        assertThat(dateItems.countBySourceKind(DateItem.SOURCE_SCHEDULE)).isEqualTo(stored);

        // 학교가 날짜를 고치면 같은 항목이 갱신되고 옛 날짜는 남지 않는다.
        var entries = new ArrayList<>(com.cukalimi.schedule.ScheduleParser.parse(Fixtures.read("calendar-2026.html"), 2026));
        var moved = entries.stream().filter(e -> e.title().equals("재학생 수강신청")).findFirst().orElseThrow();
        entries.set(entries.indexOf(moved), new com.cukalimi.schedule.ScheduleEntry(moved.title(),
                moved.startsOn().plusDays(1), moved.endsOn().plusDays(1), moved.campuses()));
        ScheduleStore.Result third = ((com.cukalimi.schedule.ScheduleStore) org.springframework.test.util.ReflectionTestUtils
                .getField(scheduleCollector, "store")).apply(2026, "성심", entries);
        assertThat(third.updated()).isEqualTo(1);
        assertThat(third.inserted()).isZero();
        assertThat(dateItems.findByNaturalKey("schedule:2026:재학생 수강신청#1")).get()
                .satisfies(i -> assertThat(i.getStartsOn()).isEqualTo(LocalDate.of(2026, 8, 5)));
        assertThat(dateItems.countBySourceKind(DateItem.SOURCE_SCHEDULE)).isEqualTo(stored);
    }

    @Test
    void benefitsAreSeededAndChangesReachTheDatabase() {
        List<BenefitSeed> seeds = BenefitSeedFile.load();
        long count = benefitRepository.count();
        assertThat(count).isGreaterThanOrEqualTo(15);

        BenefitStore.Result again = benefitStore.seed(seeds);
        assertThat(again.inserted()).isZero();
        assertThat(again.updated()).isZero();

        // 파일에서 한 항목의 가격을 바꾸고 다른 항목 하나를 빼면 DB에도 그대로 반영된다.
        List<BenefitSeed> changed = new ArrayList<>();
        for (BenefitSeed s : seeds) {
            if (s.slug().equals("spotify-student")) {
                changed.add(new BenefitSeed(s.slug(), s.name(), s.category(), s.summary(), s.condition(), s.howTo(),
                        "월 7,000원", s.sourceName(), s.sourceUrl(), s.verifiedOn(), s.renewalRule(), s.endsOn()));
            } else if (!s.slug().equals("figma-education")) {
                changed.add(s);
            }
        }
        BenefitStore.Result third = benefitStore.seed(changed);
        assertThat(third.updated()).isEqualTo(1);
        assertThat(third.deactivated()).isEqualTo(1);
        assertThat(benefitRepository.findBySlug("spotify-student").orElseThrow().getPrice()).isEqualTo("월 7,000원");
        assertThat(benefitRepository.findBySlug("figma-education").orElseThrow().isActive()).isFalse();

        // 원래 파일로 돌리면 둘 다 되돌아온다.
        BenefitStore.Result back = benefitStore.seed(seeds);
        assertThat(back.updated()).isEqualTo(2);
        assertThat(benefitRepository.findBySlug("figma-education").orElseThrow().isActive()).isTrue();
    }
}
