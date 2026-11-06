package com.cukalimi.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.cukalimi.Fixtures;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoticeParsersTest {

    @Test
    void listParserDedupesPinnedRows() {
        List<NoticeSummary> rows = NoticeListParser.parse(Fixtures.read("notice-list-scholarship.html"));
        assertThat(rows).isNotEmpty();
        assertThat(rows.stream().map(NoticeSummary::articleNo).distinct().count()).isEqualTo(rows.size());
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.category()).isEqualTo("장학");
            assertThat(r.title()).isNotBlank();
            assertThat(r.postedOn()).isAfter(LocalDate.of(2025, 1, 1));
        });
        assertThat(rows).anyMatch(r -> r.articleNo() == 276772L);
    }

    @Test
    void listParserReadsAcademicCategory() {
        List<NoticeSummary> rows = NoticeListParser.parse(Fixtures.read("notice-list-academic.html"));
        assertThat(rows).allSatisfy(r -> assertThat(r.category()).isEqualTo("학사"));
        assertThat(rows).anyMatch(r -> r.articleNo() == 276997L);
    }

    @Test
    void viewParserReadsHeaderAndBody() {
        NoticeView v = NoticeViewParser.parse(Fixtures.read("notice-273306.html"));
        assertThat(v.articleNo()).isEqualTo(273306L);
        assertThat(v.category()).isEqualTo("학사");
        assertThat(v.title()).contains("2026학년도 2학기 수강신청 일정 안내");
        assertThat(v.postedOn()).isEqualTo(LocalDate.of(2026, 6, 29));
        assertThat(v.writer()).isEqualTo("학사지원팀");
        assertThat(v.bodyText()).contains("수강바구니");
        assertThat(v.attachments()).anyMatch(a -> a.contains("예정학년"));
        assertThat(v.cleanTitle()).isEqualTo("2026학년도 2학기 수강신청 일정 안내");
    }

    @Test
    void courseRegistrationTableBecomesEightItemsPlusTheReturnPeriodInText() {
        NoticeView v = NoticeViewParser.parse(Fixtures.read("notice-273306.html"));
        List<ExtractedItem> items = DateItemExtractor.extract(v);
        items.forEach(i -> System.out.println("  " + i));
        // 수강바구니 2행, 학년별 본 수강신청 4행, 변경기간 2행, 그리고 문단에만 적힌 복학 신청 기간
        assertThat(items).hasSize(9);
        assertThat(items.get(8).title()).isEqualTo("2026-2학기 복학 신청 기간");
        assertThat(items.get(8).range().end()).isEqualTo(LocalDate.of(2026, 7, 10));

        ExtractedItem basket = items.get(0);
        assertThat(basket.title()).contains("수강바구니").contains("재학생");
        assertThat(basket.range().start()).isEqualTo(LocalDate.of(2026, 7, 7));
        assertThat(basket.range().end()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(basket.range().startsAt()).isEqualTo(LocalTime.of(9, 0));
        assertThat(basket.range().endsAt()).isEqualTo(LocalTime.of(23, 59));
        assertThat(basket.targetGrade()).isEqualTo("ALL");

        ExtractedItem fourth = items.get(2);
        assertThat(fourth.title()).contains("본 수강신청").contains("4");
        assertThat(fourth.range().start()).isEqualTo(LocalDate.of(2026, 8, 4));
        assertThat(fourth.range().startsAt()).isEqualTo(LocalTime.of(9, 0));
        assertThat(fourth.range().endsAt()).isEqualTo(LocalTime.of(15, 0));
        assertThat(fourth.targetGrade()).isEqualTo("4");

        ExtractedItem second = items.get(7);
        assertThat(second.title()).contains("변경기간");
        assertThat(second.range().start()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(second.range().end()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(second.range().endsAt()).isEqualTo(LocalTime.of(17, 0));
        assertThat(second.targetGrade()).isEqualTo("ALL");
    }

    /** 2026-11-04 홈 점검에서 나온 표. 설명 칸이 제목에 붙고, 환불 행은 날짜 열 둘에서 항목이 넷 나왔다. */
    @Test
    void seasonalRegistrationTableKeepsLabelsShortAndMergesEqualDateColumns() {
        NoticeView v = NoticeViewParser.parse(Fixtures.read("notice-277372.html"));
        List<ExtractedItem> items = DateItemExtractor.extract(v);
        List<String> titles = items.stream().map(ExtractedItem::title).toList();
        // 할 일 줄은 공지의 대상 이름("겨울 계절학기")을 앞에 붙여 정규 수강신청과 헷갈리지 않게 한다.
        assertThat(titles).contains("겨울 계절학기 수강신청", "폐강과목 공고 (1차)", "겨울 계절학기 수강신청 변경/취소 및 추가신청",
                "겨울 계절학기 고지서 출력 및 수강료 납부 (등록)");
        // 설명 칸("▶ 수강신청 인원이 15명 미만인 경우…")은 제목에 붙지 않고, 자리 이름("구분", "일정")도 제목이 되지 않는다.
        assertThat(titles).allSatisfy(t -> assertThat(t).doesNotContain("▶").doesNotContain("구분 ·").doesNotContain("일정 ·"));
        assertThat(titles).allSatisfy(t -> assertThat(t.length()).isLessThanOrEqualTo(40));
        // 환불 행은 12.21~12.24가 두 열에 있어도 열마다 항목을 내지 않는다. 표 둘에 걸쳐 셋(환불신청 (2차), 2/3 환불, 환불 신청일)이고 전에는 다섯이었다.
        assertThat(items.stream().filter(i -> i.range().end().equals(java.time.LocalDate.of(2026, 12, 24))).count()).isEqualTo(3);
        // "폐강과목 공고(1차)"는 끝의 괄호를 떼면 공고라 안내다.
        assertThat(com.cukalimi.feed.ItemKind.of("폐강과목 공고 (1차)")).isEqualTo(com.cukalimi.feed.ItemKind.INFO);
        // 열 이름과 라벨이 같으면("성적확인 및 오류정정") 한 번만 적는다.
        assertThat(titles).doesNotContain("겨울 계절학기 성적확인 및 오류정정 · 성적확인 및 오류정정").contains("겨울 계절학기 성적확인 및 오류정정");
        assertThat(com.cukalimi.feed.ItemKind.of("성적확인 및 오류정정")).isEqualTo(com.cukalimi.feed.ItemKind.ACTION);
    }

    @Test
    void imageOnlyNoticeYieldsNothing() {
        NoticeView v = NoticeViewParser.parse(Fixtures.read("notice-274128.html"));
        assertThat(v.hasImages()).isTrue();
        assertThat(v.bodyText().length()).isLessThan(80);
        assertThat(v.attachments()).anyMatch(a -> a.contains("우선 수강신청 과목 목록"));
        assertThat(DateItemExtractor.extract(v)).isEmpty();
    }

    @Test
    void scholarshipTitleCarriesTheDeadline() {
        NoticeView v = NoticeViewParser.parse(Fixtures.read("notice-276772.html"));
        List<ExtractedItem> items = DateItemExtractor.extract(v);
        items.forEach(i -> System.out.println("  " + i));
        assertThat(items).isNotEmpty();
        ExtractedItem first = items.get(0);
        assertThat(first.where()).isEqualTo("title");
        assertThat(first.title()).startsWith("2027년도 두산연강재단");
        assertThat(first.range().start()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(first.range().end()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(first.range().endsAt()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void leaveDeadlineTakesHeaderAsTitleAndTimeAsDeadline() {
        NoticeView v = NoticeViewParser.parse(Fixtures.read("notice-276997.html"));
        List<ExtractedItem> items = DateItemExtractor.extract(v);
        assertThat(items).hasSize(1);
        ExtractedItem item = items.get(0);
        assertThat(item.title()).startsWith("일반휴학원 제출 마감일");
        assertThat(item.range().end()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(item.range().startsAt()).isNull();
        assertThat(item.range().endsAt()).isEqualTo(LocalTime.of(17, 30));
        assertThat(item.where()).isEqualTo("table1.r2.c0");
        assertThat(item.naturalKey(276997L, 1)).startsWith("notice:276997:t:일반휴학원제출마감일").endsWith("#1");
    }

    @Test
    void rowWithTwoDateColumnsYieldsOneItemPerColumn() {
        String body = "<table><tr><td>구분</td><td>신청기간</td><td>결과발표</td></tr>"
                + "<tr><td>1차 신청</td><td>10.27(화)~10.30(금)</td><td>11.20(금)</td></tr></table>";
        NoticeView v = new NoticeView(1L, "장학", "[학생지원팀] 테스트 장학 안내", LocalDate.of(2026, 10, 1), "",
                org.jsoup.Jsoup.parseBodyFragment(body).body(), body, false, List.of());
        List<ExtractedItem> items = DateItemExtractor.extract(v);
        assertThat(items).hasSize(2);
        // 할 일 줄은 자리 이름 칸("신청기간")을 떼고 공지의 대상 이름을 붙인다.
        assertThat(items.get(0).title()).isEqualTo("테스트 장학 1차 신청");
        assertThat(items.get(0).range().end()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(items.get(1).title()).isEqualTo("테스트 장학 결과발표 · 1차 신청");
        assertThat(items.get(1).range().end()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void rowWithTwoDateColumnsAndNoHeaderIsLeftForReview() {
        String body = "<table><tr><td>1차 신청</td><td>10.27(화)~10.30(금)</td><td>11.20(금)</td></tr></table>";
        NoticeView v = new NoticeView(1L, "장학", "테스트", LocalDate.of(2026, 10, 1), "",
                org.jsoup.Jsoup.parseBodyFragment(body).body(), body, false, List.of());
        assertThat(DateItemExtractor.extract(v)).isEmpty();
    }

    // 아래 본문은 그림만 있는 공지를 받아 적은 글에서 가져왔다(276848, 277004, 276842, 274402).

    @Test
    void birthDateConditionIsNotADeadline() {
        List<ExtractedItem> inTable = extract("<table><tr><td>접수기간</td><td>2026년 10월 16일 ~ 11월 16일</td></tr>"
                + "<tr><td>연령</td><td>만 30세 미만(1996년 10월 15일 이후 출생자)</td></tr></table>");
        assertThat(inTable).extracting(i -> i.range().end()).containsExactly(LocalDate.of(2026, 11, 16));

        // 표에 출생일만 남고 기간은 문단에 있으면 문단에서 읽어야 한다.
        List<ExtractedItem> inLine = extract("<p>접수기간 2026년 10월 16일 ~ 11월 16일</p>"
                + "<table><tr><td>연 령</td><td>만 30세 미만(1996년 10월 15일 이후 출생자)</td></tr></table>");
        assertThat(inLine).extracting(i -> i.range().end()).containsExactly(LocalDate.of(2026, 11, 16));
    }

    @Test
    void labelOnItsOwnLineNamesTheDatesBelow() {
        List<ExtractedItem> items = extract("<p>신청기간</p><p>2026. 10. 22.(목) 9시 ~ 11. 12.(목) 18시</p>"
                + "<p>결과발표</p><p>2026년 11월 말 예정</p>");
        assertThat(items).hasSize(1);
        // "신청기간"은 자리 이름이라 공지 제목(여기서는 "테스트")이 할 일이 된다. 일의 이름이 적힌 줄은 그대로 남는다.
        assertThat(items.get(0).title()).isEqualTo("테스트");
        assertThat(items.get(0).range().end()).isEqualTo(LocalDate.of(2026, 11, 12));
        List<ExtractedItem> named = extract("<p>서류 제출</p><p>2026. 10. 22.(목) 9시 ~ 11. 12.(목) 18시</p>");
        assertThat(named).singleElement().satisfies(i -> assertThat(i.title()).isEqualTo("서류 제출"));

        List<ExtractedItem> dashed = extract("<p>모집기간</p><p>2026.<br>10.08(목)<br>–<br>10.29(목)<br>🕒 15:00</p>");
        assertThat(dashed).hasSize(1);
        assertThat(dashed.get(0).range().start()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(dashed.get(0).range().end()).isEqualTo(LocalDate.of(2026, 10, 29));
    }

    /** 2026-11-06 점검에서 나온 과목 표. 과목마다 수업 기간이 있지만 할 일은 수강신청 하나다. */
    @Test
    void classTimeColumnIsNotADeadline() {
        List<ExtractedItem> items = extract("<table><tr><td>수강신청</td><td>2026.11.6.(금)9시~12.9.(수)17시</td></tr>"
                + "<tr><td>수강기간</td><td>2026.12.16.(수)~2027.1.18.(월)</td></tr></table>"
                + "<table><tr><td>연번</td><td>CU12 영역</td><td>과목명</td><td>수업형태</td><td>수업교시</td></tr>"
                + "<tr><td>1</td><td>인문학</td><td>OTT로읽는철학</td><td>혼합형</td><td>12/21~1/12 1~3교시</td></tr>"
                + "<tr><td>2</td><td>인문학</td><td>가깝고도 먼 중국 이야기</td><td>비동시적</td><td>12/28~1/18</td></tr></table>");
        List<ExtractedItem> actions = items.stream().filter(i -> com.cukalimi.feed.ItemKind.ACTION.equals(com.cukalimi.feed.ItemKind.of(i.title()))).toList();
        assertThat(actions).singleElement().satisfies(i -> {
            assertThat(i.title()).isEqualTo("수강신청");
            assertThat(i.range().end()).isEqualTo(LocalDate.of(2026, 12, 9));
        });
        assertThat(items).noneMatch(i -> i.title().contains("철학") || i.title().contains("중국"));
    }

    /** 2026-11-04 점검에서 나온 줄. 날짜가 괄호 안이고 앞에 링크 글자가 붙었다. */
    @Test
    void periodInParenthesesNamesTheThingBeforeIt() {
        List<ExtractedItem> items = extract("<p>6. 신청 방법</p><p>- 1기:&nbsp; 1기 신청 바로가기 &nbsp;(신청기간: 11.03.화~11.11.수)</p>"
                + "<p>- 2기:&nbsp; 2기 신청 바로가기 &nbsp;(신청기간:&nbsp; 11.03.화~11.18. 수)</p>");
        assertThat(items).extracting(ExtractedItem::title).containsExactly("1기 신청", "2기 신청");
        assertThat(items).extracting(i -> i.range().end()).containsExactly(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 18));
    }

    @Test
    void labelInsideTheDateCellNamesTheItem() {
        List<ExtractedItem> items = extract("<p>인간학 가을 특강</p><table><tr><td>일시 : 2026.11.4.(수), 16:00 ~ 18:00</td></tr>"
                + "<tr><td>장소 : 콘서트홀</td></tr></table>");
        assertThat(items).singleElement().satisfies(i -> {
            assertThat(i.title()).isEqualTo("일시");
            assertThat(com.cukalimi.feed.ItemKind.of(i.title())).isEqualTo(com.cukalimi.feed.ItemKind.INFO);
        });
    }

    @Test
    void headingAboveNamesTheDatesBelow() {
        List<ExtractedItem> items = extract("<h3>3. 추천서 접수</h3><ol><li><p>2026.09.28.(월) 09:00 ~ 10. 30.(금) 18:00</p></li></ol>");
        assertThat(items).singleElement().satisfies(i -> {
            assertThat(i.title()).isEqualTo("추천서 접수");
            assertThat(i.range().end()).isEqualTo(LocalDate.of(2026, 10, 30));
        });
    }

    @Test
    void separateDeadlinesInOneCellStaySeparate() {
        List<ExtractedItem> items = extract("<table><tr><td>구분</td><td>내용</td></tr><tr><td>신청서 접수</td>"
                + "<td>1차 제출(학점교류 신청) : ~ 2026. 11. 10.(화) 16:00까지<br>2차 제출(수강신청 확정 후) : ~ 2026. 12. 21.(월) 16:00까지</td></tr></table>");
        assertThat(items).extracting(i -> i.range().end())
                .containsExactly(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 21));
    }

    @Test
    void longSentenceAboveIsNotALabel() {
        assertThat(extract("<p>신청 자격을 모두 갖춘 학생은 아래 기간 안에 신청서를 제출하시기 바랍니다</p>"
                + "<p>2026. 10. 22.(목) ~ 11. 12.(목)</p>")).isEmpty();
    }

    @Test
    void recordDateColumnIsNotADeadline() {
        assertThat(extract("<table><tr><td>학부(과)</td><td>교과목명</td><td>변경내용</td><td>적용일자</td></tr>"
                + "<tr><td>세무회계금융학과</td><td>정부및비영리법인회계(07112)</td><td>폐강</td><td>2026.08.03.</td></tr>"
                + "<tr><td>영어영문학부</td><td>영어에세이쓰기2(04362)</td><td>폐강</td><td>2026.08.04.</td></tr></table>"))
                .isEmpty();
    }

    private static List<ExtractedItem> extract(String body) {
        NoticeView v = new NoticeView(1L, "장학", "테스트", LocalDate.of(2026, 10, 14), "",
                org.jsoup.Jsoup.parseBodyFragment(body).body(), body, false, List.of());
        return DateItemExtractor.extract(v);
    }

    @Test
    void academicYearIsNotAGrade() {
        assertThat(DateItemExtractor.gradeOf("2024학년도 입학생 대상")).isNull();
        assertThat(DateItemExtractor.gradeOf("3학년 이상")).isEqualTo("3");
        assertThat(DateItemExtractor.gradeOf("4학년 (약대 4학년 이상)")).isEqualTo("4");
        assertThat(DateItemExtractor.gradeOf("2~4학년")).isNull();
    }

    @Test
    void listParserTakesPostedDateFromDateColumnNotTitle() {
        String html = "<table><tbody><tr><td class=\"b-num-box\">1</td><td>학사</td>"
                + "<td class=\"b-td-title b-td-left\"><a class=\"b-title\" data-article-no=\"1\" href=\"?mode=view&articleNo=1\">"
                + "교과목 변동사항 안내 &lt;2026.09.04. 업데이트&gt;</a></td>"
                + "<td>2026.08.20</td><td>학사행정팀</td><td>10</td></tr></tbody></table>";
        List<NoticeSummary> rows = NoticeListParser.parse(html);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).title()).contains("2026.09.04");
        assertThat(rows.get(0).postedOn()).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void graduationNoticeKeepsItsCeremonyDayForTheBoard() {
        NoticeView view = NoticeViewParser.parse(Fixtures.read("gold/notices/274824.html"));

        var ceremony = DateItemExtractor.extract(view).stream()
                .filter(i -> i.range().end().equals(java.time.LocalDate.of(2026, 8, 20))).toList();

        org.assertj.core.api.Assertions.assertThat(ceremony).isNotEmpty();
        org.assertj.core.api.Assertions.assertThat(ceremony).anyMatch(i -> com.cukalimi.feed.ItemKind.isEvent(
                i.title(), "후기 학위수여식 및 포토 스팟", i.range().start() == null || i.range().start().equals(i.range().end())));
    }

    @Test
    void bareLabelGetsTheNoticeSubject() {
        NoticeView view = NoticeViewParser.parse(Fixtures.read("gold/notices/276264.html"));

        List<String> titles = DateItemExtractor.extract(view).stream().map(ExtractedItem::title).toList();

        assertThat(titles).contains("하나장학생 트리니티 신청", "하나장학생 원본서류 제출").doesNotContain("트리니티 신청");
        assertThat(DateItemExtractor.subjectOf("2학기 푸른등대 기부장학금(2차) 신청").name()).isEqualTo("기부장학금");
        assertThat(DateItemExtractor.subjectOf("2027년도 두산연강재단 대학 장학생 선발").name()).isEqualTo("두산연강재단 장학생");
        assertThat(DateItemExtractor.subjectOf("「바이오헬스 일자리 아카데미」 참여자 모집 안내").name()).isEqualTo("바이오헬스 아카데미");
        assertThat(DateItemExtractor.subjectOf("2026학년도 2학기 직무인턴 2차 신청 안내").name()).isEqualTo("직무인턴 2차");
        assertThat(DateItemExtractor.subjectOf("2026학년도 겨울 계절학기 수강신청 안내").name()).isEqualTo("겨울 계절학기");
        assertThat(DateItemExtractor.subjectOf("27년 전기 1차 대학원 신입생 모집 안내").name()).isEqualTo("대학원");
        assertThat(DateItemExtractor.subjectOf("2학기 학생설계전공 신규·변경·포기 신청")).isNull();
        assertThat(DateItemExtractor.subjectOf("26년 가톨릭대학교 Programming 대회 안내").name()).isEqualTo("Programming 대회");
        // 라벨이 이미 대상을 말하면 붙이지 않는다.
        assertThat(DateItemExtractor.subjectOf("2026학년도 겨울 계절학기 수강신청 안내").namedIn("계절학기 수강변경")).isTrue();
        assertThat(DateItemExtractor.subjectOf("2026학년도 2학기 교육지원인턴 신청 안내").namedIn("트리니티 신청 및 근로지 자기소개서 제출")).isFalse();
    }
}
