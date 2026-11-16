package com.cukalimi.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.cukalimi.notice.LocatorReading.Found;
import com.cukalimi.notice.QuoteCheck.Located;
import com.cukalimi.notice.QuoteCheck.Status;
import java.time.LocalDate;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

/**
 * AI 문장 찾기의 순수 부분 (ADR 016). API를 부르지 않는다.
 * 지키는 것은 "날짜 파서에는 공지의 글자만 들어간다"이다. AI가 지어낸 인용은 버리고, 날짜는 원문의 그 줄에서 읽는다.
 */
class LocatorCoreTest {

    private static final LocalDate POSTED = LocalDate.of(2026, 11, 2);

    private static NoticeView view(String title, String bodyHtml) {
        return new NoticeView(1L, "학사", title, POSTED, "", Jsoup.parseBodyFragment(bodyHtml).body(), "", false, List.of());
    }

    private static List<Located> read(NoticeView view, Found... found) {
        return QuoteCheck.check(NoticeText.of(view), view.postedOn(), new LocatorReading(List.of(found)));
    }

    /** 규칙이 놓치던 꼴. "기간"이 아니라 "기한"이고, 본문 div에 바로 적혀 p나 li가 아니다. */
    private static final NoticeView LEAVE = view("[학사지원팀] 2026-2학기 공결 신청 안내",
            "<div>1. 대상 : 재학생<br>2. 7일이내 증빙서류를 제출<br>3. 공결 신청 기한 : 11월 25일(수) 17:00까지</div>"
                    + "<table><tr><th>구분</th><th>일정</th></tr><tr><td>결과 발표</td><td>12. 1.(화)</td></tr></table>");

    @Test
    void noticeTextTurnsBodyTextBreaksAndTableRowsIntoLines() {
        assertThat(NoticeText.of(LEAVE).lines()).containsExactly("[학사지원팀] 2026-2학기 공결 신청 안내", "1. 대상 : 재학생", "2. 7일이내 증빙서류를 제출",
                "3. 공결 신청 기한 : 11월 25일(수) 17:00까지", "구분 | 일정", "결과 발표 | 12. 1.(화)");
    }

    /** 학교 편집기는 칸마다 문단을 넣는다. 칸마다 줄이 나뉘면 이름과 날짜가 떨어져 AI도 인용 확인도 행을 못 본다. */
    @Test
    void paragraphsAndBreaksInsideATableCellDoNotSplitTheRow() {
        NoticeView view = view("[학생지원팀] 교육지원인턴 신청 안내",
                "<table><tr><td><p>신청기간</p></td><td><p>2026.11.17.(화)~</p><p>2026.11.27.(금)</p></td><td><div>트리니티 신청 및<br>자기소개서 제출</div></td></tr></table>"
                        + "<p>문의 : 학생지원팀</p>");

        assertThat(NoticeText.of(view).lines()).containsExactly("[학생지원팀] 교육지원인턴 신청 안내",
                "신청기간 | 2026.11.17.(화)~ 2026.11.27.(금) | 트리니티 신청 및 자기소개서 제출", "문의 : 학생지원팀");
    }

    @Test
    void aTableInsideACellKeepsItsOwnRowsAsLines() {
        NoticeView view = view("[취업지원팀] 특강 안내", "<table><tr><td>일정<table><tr><td>10.26.(월)</td><td>OT</td></tr>"
                + "<tr><td>11.02.(월)</td><td>기업분석 특강</td></tr></table></td></tr></table>");

        assertThat(NoticeText.of(view).lines()).containsExactly("[취업지원팀] 특강 안내", "일정", "10.26.(월) | OT", "11.02.(월) | 기업분석 특강");
    }

    @Test
    void theRulesMissThisDeadlineWhichIsWhyTheLocatorExists() {
        assertThat(LocatorMerge.ruleDeadlines(DateItemExtractor.extract(LEAVE))).doesNotContain(LocalDate.of(2026, 11, 25));
    }

    @Test
    void aVerbatimQuoteIsReadByTheParserFromTheNoticeLine() {
        List<Located> located = read(LEAVE, new Found("공결 신청 기한 : 11월 25일(수) 17:00까지", "공결 신청", true));

        assertThat(located).hasSize(1);
        assertThat(located.get(0).status()).isEqualTo(Status.READ);
        assertThat(located.get(0).source()).isEqualTo("3. 공결 신청 기한 : 11월 25일(수) 17:00까지");
        assertThat(located.get(0).ranges()).extracting(r -> r.end()).containsExactly(LocalDate.of(2026, 11, 25));
    }

    @Test
    void spacingDifferencesAndTableSeparatorsDoNotBreakTheMatch() {
        List<Located> located = read(LEAVE, new Found("공결신청 기한: 11월 25일 (수) 17:00까지", "신청", true),
                new Found("결과 발표 12. 1.(화)", "발표", false));

        assertThat(located).extracting(Located::status).containsExactly(Status.READ, Status.READ);
        assertThat(located.get(1).source()).isEqualTo("결과 발표 | 12. 1.(화)");
    }

    @Test
    void anInventedOrRewrittenQuoteIsThrownAwaySoAMadeUpDateCannotGetOut() {
        List<Located> located = read(LEAVE, new Found("공결 신청 기한 : 11월 27일(금) 17:00까지", "신청", true),
                new Found("공결 신청은 11월 25일 수요일까지 가능합니다", "신청", true));

        assertThat(located).extracting(Located::status).containsOnly(Status.UNVERIFIED);
        assertThat(located).allMatch(l -> l.ranges().isEmpty());
    }

    @Test
    void aQuoteWithoutDigitsOrTooShortIsNotAccepted() {
        assertThat(read(LEAVE, new Found("공결 신청 기한", "신청", true), new Found("11월 25", "신청", true)))
                .extracting(Located::status).containsOnly(Status.UNVERIFIED);
    }

    @Test
    void aRealLineWhoseNumberIsNotADateIsKeptAsANoteOnly() {
        List<Located> located = read(LEAVE, new Found("1. 대상 : 재학생", "대상", true));

        assertThat(located.get(0).status()).isEqualTo(Status.NO_DATE);
    }

    /**
     * 알려진 한계. 날짜는 규칙의 파서가 읽으므로 파서가 잘못 읽는 꼴은 AI를 거쳐도 그대로다.
     * "2. 7일이내"의 번호와 기간을 2월 7일로 읽는다. AI가 이 줄을 마감이라고 옮겨 오지 않는 한 화면에는 나가지 않는다.
     */
    @Test
    void theParserStillMisreadsANumberedDurationSoTheLocatorInheritsThatLimit() {
        List<Located> located = read(LEAVE, new Found("2. 7일이내 증빙서류를 제출", "서류 제출", true));

        assertThat(located.get(0).status()).isEqualTo(Status.READ);
        assertThat(located.get(0).ranges()).extracting(r -> r.end()).containsExactly(LocalDate.of(2027, 2, 7));
    }

    @Test
    void twoAgreeingReadsAddTheMissedDeadlineWithItsEvidence() {
        List<ExtractedItem> rule = DateItemExtractor.extract(LEAVE);
        List<Located> a = read(LEAVE, new Found("3. 공결 신청 기한 : 11월 25일(수) 17:00까지", "공결 신청", true));
        List<Located> b = read(LEAVE, new Found("공결 신청 기한 : 11월 25일(수) 17:00까지", "신청", true));

        assertThat(LocatorMerge.disagree(rule, a, b)).isFalse();
        LocatorMerge.Result merged = LocatorMerge.merge(LEAVE, rule, List.of(a, b));

        assertThat(merged.held()).isEmpty();
        assertThat(merged.added()).hasSize(1);
        ExtractedItem item = merged.added().get(0);
        assertThat(item.range().end()).isEqualTo(LocalDate.of(2026, 11, 25));
        // 이름은 AI의 말이 아니라 날짜 앞에 적힌 원문 글자다.
        assertThat(item.title()).isEqualTo("공결 신청 기한");
        assertThat(item.evidence().text()).isEqualTo("3. 공결 신청 기한 : 11월 25일(수) 17:00까지");
        assertThat(item.evidence().text()).contains(item.evidence().match());
        assertThat(item.evidence().match()).contains("11월 25일");
    }

    @Test
    void oneReadAloneIsHeldAndTheDisagreementCallsForAThirdRead() {
        List<ExtractedItem> rule = DateItemExtractor.extract(LEAVE);
        List<Located> a = read(LEAVE, new Found("공결 신청 기한 : 11월 25일(수) 17:00까지", "신청", true));
        List<Located> b = read(LEAVE);

        assertThat(LocatorMerge.disagree(rule, a, b)).isTrue();
        assertThat(LocatorMerge.merge(LEAVE, rule, List.of(a, b)).added()).isEmpty();
        assertThat(LocatorMerge.merge(LEAVE, rule, List.of(a, b)).held()).hasSize(1);

        // 큰 모델이 한 번 더 읽어 같은 줄을 가리키면 셋 중 둘이라 공개한다.
        List<Located> third = read(LEAVE, new Found("공결 신청 기한 : 11월 25일(수) 17:00까지", "공결 신청", true));
        assertThat(LocatorMerge.merge(LEAVE, rule, List.of(a, b, third)).added()).hasSize(1);
    }

    /** 규칙이 시험일을 마감으로 읽는 꼴. */
    private static final NoticeView EXAM = view("[컴퓨터정보공학부] 2026-2학기 졸업시험 안내",
            "<p>신청기간 : 10. 12.(월) ~ 11. 6.(금)</p><p>시험일 접수 : 11. 16.(월) 저녁 예정</p>");

    @Test
    void aRuleDeadlineTheAiCallsNotAnActionIsDoubtedButNeverRemoved() {
        List<ExtractedItem> rule = DateItemExtractor.extract(EXAM);
        assertThat(LocatorMerge.ruleDeadlines(rule)).contains(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 16));
        Found apply = new Found("신청기간 : 10. 12.(월) ~ 11. 6.(금)", "졸업시험 신청", true);
        Found exam = new Found("시험일 접수 : 11. 16.(월) 저녁 예정", "시험", false);
        List<Located> a = read(EXAM, apply, exam);
        List<Located> b = read(EXAM, apply, exam);

        LocatorMerge.Result merged = LocatorMerge.merge(EXAM, rule, List.of(a, b));

        assertThat(merged.doubt()).containsExactly(LocalDate.of(2026, 11, 16));
        assertThat(merged.added()).isEmpty();
        assertThat(merged.held()).isEmpty();
    }

    @Test
    void aSingleNoVoteOrASilentAiLeavesTheRuleDeadlineUnmarked() {
        List<ExtractedItem> rule = DateItemExtractor.extract(EXAM);
        List<Located> says = read(EXAM, new Found("시험일 접수 : 11. 16.(월) 저녁 예정", "시험", false));
        List<Located> silent = read(EXAM);

        assertThat(LocatorMerge.disagree(rule, says, silent)).isTrue();
        assertThat(LocatorMerge.merge(EXAM, rule, List.of(says, silent)).doubt()).isEmpty();
        assertThat(LocatorMerge.merge(EXAM, rule, List.of(silent, silent)).doubt()).isEmpty();
    }

    @Test
    void agreeingOnADeadlineTheRulesAlreadyFoundChangesNothingAndNeedsNoThirdRead() {
        List<ExtractedItem> rule = DateItemExtractor.extract(EXAM);
        List<Located> a = read(EXAM, new Found("신청기간 : 10. 12.(월) ~ 11. 6.(금)", "신청", true));
        List<Located> silent = read(EXAM);

        assertThat(LocatorMerge.disagree(rule, a, silent)).isFalse();
        LocatorMerge.Result merged = LocatorMerge.merge(EXAM, rule, List.of(a, silent));
        assertThat(merged.added()).isEmpty();
        assertThat(merged.held()).isEmpty();
        assertThat(merged.doubt()).isEmpty();
    }

    @Test
    void tooManyAiOnlyDeadlinesInOneNoticeAreAllHeld() {
        StringBuilder body = new StringBuilder("<div>");
        Found[] found = new Found[LocatorMerge.MAX_ADDED + 1];
        for (int i = 0; i < found.length; i++) {
            String line = (i + 1) + "차 서류 제출 기한 : 12월 " + (i + 1) + "일까지";
            body.append(line).append("<br>");
            found[i] = new Found(line, "서류 제출", true);
        }
        NoticeView many = view("[학사지원팀] 서류 제출 안내", body.append("</div>").toString());
        List<Located> a = read(many, found);

        LocatorMerge.Result merged = LocatorMerge.merge(many, DateItemExtractor.extract(many), List.of(a, a));

        assertThat(merged.added()).isEmpty();
        assertThat(merged.held()).hasSize(found.length);
    }

    @Test
    void aDateOutsideTheYearAfterPostingIsNotADeadline() {
        NoticeView born = view("[학생지원팀] 장학생 선발 안내", "<div>지원 자격 : 2001년 10월 15일 이후 출생자</div>");

        assertThat(read(born, new Found("지원 자격 : 2001년 10월 15일 이후 출생자", "지원", true)).get(0).status()).isEqualTo(Status.NO_DATE);
    }
}
