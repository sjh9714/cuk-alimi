package com.campusalimi.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusalimi.domain.DateItem;
import com.campusalimi.domain.Notice;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FeedTest {

    static final LocalDate D = LocalDate.of(2026, 10, 27);
    long seq = 1;
    final List<DateItem> items = new ArrayList<>();
    final Map<Long, Notice> notices = new HashMap<>();
    final Map<Long, Audience> audiences = new HashMap<>();
    final List<Notice> undated = new ArrayList<>();

    Notice notice(String category, String title, String body) {
        Notice n = new Notice(seq, category, title, D.minusDays(3), "", "https://school/" + seq, body, false, "", Notice.REASON_PARSED);
        ReflectionTestUtils.setField(n, "id", seq++);
        notices.put(n.getId(), n);
        audiences.put(n.getId(), Audience.of(n));
        return n;
    }

    DateItem item(Notice n, String title, int days) {
        DateItem i = DateItem.fromNotice(n.getId(), "k" + seq, title, null, D.plusDays(days), null, null, null);
        ReflectionTestUtils.setField(i, "id", seq++);
        items.add(i);
        return i;
    }

    DateItem schedule(String title, int days) {
        DateItem i = DateItem.fromSchedule("schedule:2026:" + title + "#1", title, null, D.plusDays(days), "성심");
        ReflectionTestUtils.setField(i, "id", seq++);
        items.add(i);
        return i;
    }

    Feed.View build(Feed.Viewer viewer) {
        return Feed.build(items, notices, audiences, undated, viewer, viewer != Feed.Viewer.ANONYMOUS, Set.of(), y -> "https://calendar/" + y);
    }

    static Feed.Section section(Feed.View v, String key) {
        return v.sections().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void noticeWithoutActionsShowsItsCeremonyAsAnEvent() {
        Notice ceremony = notice("학사", "[학사행정팀] 2025학년도 후기 학위수여식 및 포토 스팟 안내", "");
        DateItem day = item(ceremony, "일시", 3);
        item(ceremony, "운영기간", 2);

        Feed.View v = build(Feed.Viewer.ANONYMOUS);

        assertThat(v.sections()).extracting(Feed.Section::key).containsExactly("events");
        Feed.Section events = section(v, "events");
        assertThat(events.collapsed()).isTrue();
        assertThat(events.cards()).extracting(c -> c.deadline().id()).containsExactly(day.getId());
    }

    @Test
    void scheduleCeremonyIsAnEventButTermDatesAreNot() {
        schedule("봄 학위수여식", 3);
        schedule("수업기간", 4);

        assertThat(section(build(Feed.Viewer.ANONYMOUS), "events").cards()).extracting(Feed.Card::title).containsExactly("봄 학위수여식");
    }

    @Test
    void longOrNarrowEventsStayOffTheBoard() {
        DateItem week = item(notice("학사", "학생 축제 안내", ""), "축제 운영 기간", 5);
        ReflectionTestUtils.setField(week, "startsOn", D.plusDays(1));
        item(notice("장학", "순천시 장학금 수여식 안내", "전라남도 순천시 연고"), "장학증서 수여식", 3);

        assertThat(build(Feed.Viewer.ANONYMOUS).sections()).extracting(Feed.Section::key).doesNotContain("events");
    }

    @Test
    void oneNoticeIsOneCardAndTheNearestActionLeads() {
        Notice exchange = notice("학사", "[학사행정팀] 2026학년도 겨울계절학기 학점교류 안내", "");
        item(exchange, "한성대학교 · 서울", 6);
        DateItem first = item(exchange, "동국대학교 · 서울", 1);
        item(exchange, "수업기간", 40);

        Feed.Card card = section(build(Feed.Viewer.ANONYMOUS), "open").cards().get(0);
        assertThat(card.title()).isEqualTo("2026학년도 겨울계절학기 학점교류 안내");
        assertThat(card.deadline().id()).isEqualTo(first.getId());
        assertThat(card.deadline().label()).isEqualTo("동국대학교 · 서울");
        assertThat(card.next()).extracting(Feed.Line::kind).containsExactly(ItemKind.ACTION, ItemKind.INFO);
    }

    @Test
    void titleDeadlineAndTableDeadlineOnTheSameDayAreOne() {
        Notice work = notice("장학", "(한국장학재단) 2026-2학기 국가근로장학금 추가신청기간 안내(10/21~10/29)", "");
        item(work, "(한국장학재단) 2026-2학기 국가근로장학금 추가신청기간 안내", 2);
        DateItem table = item(work, "신청기간", 2);
        Feed.Card card = section(build(Feed.Viewer.ANONYMOUS), "must").cards().get(0);
        assertThat(card.deadline().id()).isEqualTo(table.getId());
        assertThat(card.next()).isEmpty();
    }

    @Test
    void tableLabelsLoseRepeatsAndAttachmentNotes() {
        assertThat(Feed.tidyLabel("서강대학교 · 서강대학교 · 서울 · 첨부파일 참조")).isEqualTo("서강대학교 · 서울");
        assertThat(Feed.tidyLabel("트리니티 신청")).isEqualTo("트리니티 신청");
    }

    @Test
    void noticeWithOnlyWatchingDatesLeft() {
        Notice mirae = notice("장학", "[학생지원팀] 제36기 미래에셋 장학생 선발 요강 안내", "");
        item(mirae, "서류 합격자 발표", 9);
        item(mirae, "최종 합격자 발표", 37);
        assertThat(build(Feed.Viewer.ANONYMOUS).sections()).isEmpty();
    }

    @Test
    void resultNoticesNeverShow() {
        Notice result = notice("장학", "[학부대학] 「이원길 장학금」 최종 선발 결과 안내", "");
        item(result, "시상식 참석", 2);
        assertThat(build(Feed.Viewer.ANONYMOUS).sections()).isEmpty();
    }

    @Test
    void scheduleAndNationalScholarshipsGoFirst() {
        schedule("수강신청", 100);
        schedule("2학기 개시일", 30);
        Notice work = notice("장학", "[학생지원팀] (한국장학재단) 2026-2학기 국가근로장학금 추가신청기간 안내(10/21~10/29)", "");
        item(work, "신청기간", 2);
        Notice other = notice("장학", "2027년도 두산연강재단 대학 장학생 선발 안내", "");
        item(other, "두산연강재단 대학 장학생 선발 안내", 1);

        Feed.View v = build(Feed.Viewer.ANONYMOUS);
        assertThat(v.sections()).extracting(Feed.Section::key).containsExactly("must", "open");
        assertThat(section(v, "must").cards()).extracting(Feed.Card::title)
                .containsExactly("(한국장학재단) 2026-2학기 국가근로장학금 추가신청기간 안내", "수강신청");
        assertThat(section(v, "must").cards().get(1).url()).isEqualTo("https://calendar/2026");
    }

    @Test
    void regionScholarshipsAreFoldedUnlessTheyAreMine() {
        Notice eunpyeong = notice("장학", "[학생지원팀] 2026년 하반기 은평구민장학재단 장학생 선발 안내", "");
        item(eunpyeong, "은평구민장학재단 장학생 선발 안내", 2);
        Notice refugee = notice("장학", "<아산사회복지재단> 2027년도 북한이탈청소년 장학생 선발 안내", "");
        item(refugee, "접수", 0);

        Feed.View anonymous = build(Feed.Viewer.ANONYMOUS);
        assertThat(anonymous.sections()).extracting(Feed.Section::key).containsExactly("region", "group");
        assertThat(anonymous.sections()).allMatch(Feed.Section::collapsed);
        assertThat(section(anonymous, "group").cards().get(0).audience().label()).isEqualTo("북한이탈청소년");

        Feed.Viewer seoul = new Feed.Viewer(2, null, null, Regions.parse("서울특별시 은평구").orElseThrow());
        Feed.View mine = build(seoul);
        assertThat(section(mine, "mine").cards()).extracting(Feed.Card::title)
                .containsExactly("2026년 하반기 은평구민장학재단 장학생 선발 안내");
        assertThat(section(mine, "mine").collapsed()).isFalse();

        Feed.Viewer busan = new Feed.Viewer(2, null, null, Regions.parse("부산광역시 중구").orElseThrow());
        assertThat(section(build(busan), "region").title()).isEqualTo("다른 지역 장학");
    }

    @Test
    void operatorTagsMakeAnItemMine() {
        Notice minor = notice("학사", "(2~4학년) 2026학년도 2학기 부전공(2차) 신청/변경 안내", "");
        DateItem i = item(minor, "신청기간", 3);
        i.review("신청기간", null, i.getEndsOn(), null, null, "3", "ALL", null, "admin");

        assertThat(section(build(new Feed.Viewer(3, "경영학과", null, null)), "mine").cards()).hasSize(1);
        assertThat(build(new Feed.Viewer(1, "경영학과", null, null)).sections()).extracting(Feed.Section::key)
                .containsExactly("open");
    }

    @Test
    void writtenGradeRangeMakesAGeneralNoticeMineButNeverHidesIt() {
        Notice minor = notice("학사", "[학사행정팀] (2~4학년) 2026학년도 2학기 부전공(2차) 신청/변경 안내", "");
        item(minor, "신청기간", 3);

        Feed.View third = build(new Feed.Viewer(3, null, null, null));
        assertThat(section(third, "mine").cards()).hasSize(1);
        assertThat(section(third, "mine").cards().get(0).audience().grade()).isEqualTo("2~4학년");
        Feed.View first = build(new Feed.Viewer(1, null, null, null));
        assertThat(first.sections()).extracting(Feed.Section::key).containsExactly("open");
        assertThat(section(first, "open").cards().get(0).audience().grade()).isEqualTo("2~4학년");
    }

    @Test
    void careerNoticesAreFoldedBelowScholarships() {
        Notice job = notice("취창업", "[대플-추천채용] 모두투어 - 채용 연계형 인턴 모집", "");
        item(job, "접수 마감", 5);
        Notice work = notice("장학", "2027년도 두산연강재단 대학 장학생 선발 안내", "");
        item(work, "두산연강재단 대학 장학생 선발 안내", 9);

        Feed.View v = build(Feed.Viewer.ANONYMOUS);
        assertThat(v.sections()).extracting(Feed.Section::key).containsExactly("open", "career");
        assertThat(section(v, "career").collapsed()).isTrue();
        assertThat(section(v, "career").cards().get(0).kind()).isEqualTo("career");
    }

    @Test
    void careerNoticeStaysFoldedEvenWhenTheGradeMatches() {
        Notice job = notice("취창업", "[대플-추천채용] 2027 신입사원 채용(졸업예정자 및 기졸업자)", "");
        item(job, "접수 마감", 5);

        Feed.View senior = build(new Feed.Viewer(4, null, null, null));
        assertThat(senior.sections()).extracting(Feed.Section::key).containsExactly("career");
    }

    @Test
    void imageOnlyNoticesWaitInAFoldedSectionWithoutADate() {
        Notice poster = notice("학사", "[학사행정팀] 2026-2학기 제1전공생 우선 수강신청 안내", "");
        undated.add(poster);
        Notice result = notice("장학", "[학생지원팀] 2026 장학생 최종 선발 결과 안내", "");
        undated.add(result);

        Feed.Section waiting = section(build(Feed.Viewer.ANONYMOUS), "undated");
        assertThat(waiting.collapsed()).isTrue();
        assertThat(waiting.cards()).extracting(Feed.Card::title).containsExactly("2026-2학기 제1전공생 우선 수강신청 안내");
        assertThat(waiting.cards().get(0).deadline()).isNull();
    }
}
