package com.cukalimi.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.cukalimi.domain.DateItem;
import com.cukalimi.domain.Notice;
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

    Notice deptNotice(String dept, String title, String body) {
        Notice n = new Notice(seq, "학과", dept, title, D.minusDays(3), "", "https://dept/" + seq, body, false, "", Notice.REASON_PARSED);
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
    void surveyNoticeOnlyOffersItsOwnSurveyAsATask() {
        // 수요조사 공지에 딸린 계절학기 일정 표. 수강신청은 다른 공지가 안내하므로 여기서는 할 일이 아니다.
        Notice survey = notice("학사", "[학부대학운영팀] 2026학년도 겨울 계절학기 개설 희망과목 수요조사", "");
        item(survey, "계절학기 수강신청", 6);
        item(survey, "계절학기 수강변경", 20);
        DateItem answer = item(survey, "희망과목 수요조사", 3);

        Feed.View v = build(Feed.Viewer.ANONYMOUS);
        Feed.Card card = v.sections().stream().flatMap(s -> s.cards().stream()).filter(c -> c.sourceTitle().contains("수요조사")).findFirst().orElseThrow();
        assertThat(card.deadline().id()).isEqualTo(answer.getId());
        assertThat(card.next()).extracting(Feed.Line::kind).doesNotContain("ACTION");
    }

    @Test
    void titleTaskStaysWhenTheOnlyOtherLineThatDayIsInfo() {
        // 학생설계전공 공지(276928). 제목 줄과 "일시" 안내 줄이 같은 날이다. 제목 줄을 빼면 할 일이 사라져 카드가 없어졌다.
        Notice n = notice("학사", "[융합전공학부] 2026-2학기 학생설계전공 신규·변경·포기 신청 안내", "");
        DateItem task = item(n, "2026-2학기 학생설계전공 신규·변경·포기 신청 안내", 9);
        item(n, "일시", 9);

        Feed.View v = build(Feed.Viewer.ANONYMOUS);
        assertThat(cardOf(v, "학생설계전공").deadline().id()).isEqualTo(task.getId());
    }

    static Feed.Card cardOf(Feed.View v, String sourceTitlePart) {
        return v.sections().stream().flatMap(s -> s.cards().stream()).filter(c -> c.sourceTitle().contains(sourceTitlePart)).findFirst()
                .orElseThrow();
    }

    /** 형광펜은 관심 주제이면서 자격에서 걸리지 않는 일이다 (ADR 014). */
    @Test
    void highlightNeedsInterestAndEligibility() {
        // 3~7학기 대상 학점교류. 자격은 되지만 기본 관심 주제가 아니다.
        Notice exchange = notice("학사", "[학사행정팀] 2026학년도 겨울계절학기 서울 및 경인지역 대학교 학점교류 안내", "");
        audiences.put(exchange.getId(), Audience.of(exchange).withAi(
                new AudienceFacts(List.of(), List.of(5, 6, 7), List.of(), List.of(), List.of(), "3~7학기")));
        item(exchange, "건국대학교 · 서울", 5);
        // 조건 없는 수강신청. 관심 주제이고 모두 대상이라 칠한다.
        Notice course = notice("학사", "2026학년도 겨울 계절학기 수강신청 안내", "");
        item(course, "수강신청", 6);
        // 소득 조건만 있는 장학. 자격을 모르지만 관심 주제라 칠한다(사용자 결정).
        Notice income = notice("장학", "[학생지원팀] 하나장학생 선발 안내", "");
        audiences.put(income.getId(), Audience.of(income).withAi(
                new AudienceFacts(List.of(), List.of(), List.of(), List.of(), List.of(AudienceFacts.Blocker.INCOME), "소득 8구간 이하")));
        item(income, "신청기간", 7);
        // 특정 계층 장학. 관심 주제여도 칠하지 않는다.
        Notice group = notice("장학", "[학생지원팀] 북한이탈청소년 장학생 선발 안내", "");
        audiences.put(group.getId(), Audience.of(group).withAi(
                new AudienceFacts(List.of(), List.of(), List.of(), List.of(), List.of(AudienceFacts.Blocker.GROUP), "북한이탈")));
        item(group, "신청기간", 8);

        Feed.Viewer me = new Feed.Viewer(3, 6, "컴퓨터정보공학부", null, null, Topic.DEFAULTS);
        Feed.View v = build(me);
        assertThat(cardOf(v, "학점교류").mine()).isFalse();
        assertThat(cardOf(v, "겨울 계절학기 수강신청").mine()).isTrue();
        assertThat(cardOf(v, "겨울 계절학기 수강신청").mineReason()).isEqualTo("계절학기");
        assertThat(cardOf(v, "하나장학생").mine()).isTrue();
        assertThat(cardOf(v, "하나장학생").mineReason()).isEqualTo("장학금");
        assertThat(cardOf(v, "북한이탈").mine()).isFalse();

        // 학점교류를 관심에 더하면 칠하고, 이유에 주제와 자격을 적는다.
        java.util.Set<Topic> withExchange = java.util.EnumSet.copyOf(Topic.DEFAULTS);
        withExchange.add(Topic.EXCHANGE);
        Feed.Card lit = cardOf(build(new Feed.Viewer(3, 6, "컴퓨터정보공학부", null, null, withExchange)), "학점교류");
        assertThat(lit.mine()).isTrue();
        assertThat(lit.mineReason()).isEqualTo("학점교류 · 5~7학기 대상");
        assertThat(lit.topic()).isEqualTo("exchange");

        // 로그인 전에는 칠하지 않는다.
        assertThat(cardOf(build(Feed.Viewer.ANONYMOUS), "겨울 계절학기 수강신청").mine()).isFalse();
    }

    @Test
    void departmentNoticeIsMineOnlyForThatDepartmentAndFoldedForOthers() {
        Notice n = deptNotice("컴퓨터정보공학부", "[중요] 2026-2학기 졸업시험 신청 및 대체요건 제출 안내", "졸업시험 신청 기간 안내");
        item(n, "졸업시험 신청", 5);

        Feed.View mineView = build(new Feed.Viewer(3, "컴퓨터정보공학부", null, null));
        Feed.Card card = section(mineView, "mine").cards().get(0);
        assertThat(card.kind()).isEqualTo("dept");
        assertThat(card.mine()).isTrue();
        assertThat(card.mineReason()).isEqualTo("졸업 · 컴퓨터정보공학부 공지");

        Feed.View secondMajor = build(new Feed.Viewer(2, "경영학과", "컴퓨터정보공학부", null));
        assertThat(section(secondMajor, "mine").cards()).hasSize(1);

        Feed.View other = build(new Feed.Viewer(2, "경영학과", null, null));
        assertThat(other.sections()).extracting(Feed.Section::key).doesNotContain("mine", "must", "open");
        assertThat(section(other, "dept").collapsed()).isTrue();
        assertThat(section(other, "dept").cards().get(0).mine()).isFalse();

        assertThat(section(build(Feed.Viewer.ANONYMOUS), "dept").cards()).hasSize(1);
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
    void generalLectureWithAWhenLineIsAnEventButCareerLectureIsNot() {
        item(notice("학사", "[교양교육팀] 2026학년도 인간학 가을 특강 안내", ""), "일시", 3);
        item(notice("취창업", "[경력개발팀] 취업 특강 안내", ""), "일시", 4);

        assertThat(section(build(Feed.Viewer.ANONYMOUS), "events").cards()).extracting(Feed.Card::title)
                .containsExactly("2026학년도 인간학 가을 특강 안내");
    }

    @Test
    void noticeWithAnApplicationAlsoShowsItsEventDay() {
        Notice briefing = notice("학사", "[학사행정팀] 겨울 계절학기 안내", "");
        item(briefing, "신청기간", 2);
        DateItem day = item(briefing, "수강 설명회", 5);

        Feed.View v = build(Feed.Viewer.ANONYMOUS);

        assertThat(section(v, "open").cards()).hasSize(1);
        assertThat(section(v, "events").cards()).singleElement().satisfies(c -> {
            assertThat(c.title()).isEqualTo("수강 설명회");
            assertThat(c.deadline().id()).isEqualTo(day.getId());
        });
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
