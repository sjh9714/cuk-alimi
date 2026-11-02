package com.cukalimi.dateparse;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class KoreanDateParserTest {

    private final KoreanDateParser parser = new KoreanDateParser(LocalDate.of(2026, 10, 1));

    @Test
    void fullRangeWithTimes() {
        List<DateRange> r = parser.parse("2026. 7. 7.(화) ~ 7. 12.(일) / 09:00 ~ 23:59");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).start()).isEqualTo(LocalDate.of(2026, 7, 7));
        assertThat(r.get(0).end()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(r.get(0).startsAt()).isEqualTo(LocalTime.of(9, 0));
        assertThat(r.get(0).endsAt()).isEqualTo(LocalTime.of(23, 59));
    }

    @Test
    void singleDayWithTimeWindow() {
        List<DateRange> r = parser.parse("2026. 8. 4.(화) 09:00 ~ 15:00");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).isSingleDay()).isTrue();
        assertThat(r.get(0).startsAt()).isEqualTo(LocalTime.of(9, 0));
        assertThat(r.get(0).endsAt()).isEqualTo(LocalTime.of(15, 0));
    }

    @Test
    void singleDayWithOneTimeIsADeadline() {
        List<DateRange> r = parser.parse("일반휴학원 제출 마감일 10.23(금) / 17:30");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).startsAt()).isNull();
        assertThat(r.get(0).endsAt()).isEqualTo(LocalTime.of(17, 30));
    }

    @Test
    void titleRangeWithoutYearUsesBaseYear() {
        List<DateRange> r = parser.parse("두산연강재단 대학 장학생 선발 안내(10. 14.(수) ~ 11. 13.(금) 18:00까지)");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).start()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(r.get(0).end()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(r.get(0).endsAt()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void endOnly() {
        List<DateRange> r = parser.parse("북한이탈청소년 장학생 선발 안내(~10.26)");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).start()).isNull();
        assertThat(r.get(0).end()).isEqualTo(LocalDate.of(2026, 10, 26));
    }

    @Test
    void untilSuffixMakesMonthDayTrustworthy() {
        List<DateRange> r = parser.parse("서류 제출 10. 30.까지");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).end()).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    void slashRangeInParens() {
        List<DateRange> r = parser.parse("국가근로장학금 추가신청기간 안내(10/21~10/29)");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).start()).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(r.get(0).end()).isEqualTo(LocalDate.of(2026, 10, 29));
    }

    @Test
    void koreanUnits() {
        List<DateRange> r = parser.parse("접수 기간: 2026년 10월 30일(금) 18:00까지");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).end()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(r.get(0).endsAt()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void yearWrapsForNextSpring() {
        List<DateRange> r = parser.parse("신청: 1. 5.(월) ~ 1. 9.(금)");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).start()).isEqualTo(LocalDate.of(2027, 1, 5));
    }

    @Test
    void yearOnlyOnTheEndAppliesBackwards() {
        List<DateRange> r = parser.parse("12. 20.(일) ~ 2027. 1. 5.(화)");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).start()).isEqualTo(LocalDate.of(2026, 12, 20));
        assertThat(r.get(0).end()).isEqualTo(LocalDate.of(2027, 1, 5));
    }

    @Test
    void loneMonthDayWithoutWeekdayIsIgnored() {
        assertThat(parser.parse("수업일수 1/4 경과")).isEmpty();
        assertThat(parser.parse("직전 학기 평점 3.0 이상")).isEmpty();
        assertThat(parser.parse("문의 02-2164-4149")).isEmpty();
        assertThat(parser.parse("2026학년도 2학기")).isEmpty();
        assertThat(parser.parse("동일 과목 1,000번 클릭")).isEmpty();
    }

    @Test
    void decimalPairsAndNumericTailsAreNotDates() {
        assertThat(parser.parse("신청 자격: 직전학기 평점 2.5 ~ 3.5")).isEmpty();
        assertThat(parser.parse("평점 4.5점 만점")).isEmpty();
        assertThat(parser.parse("3.0 이상 ~ 4.0 이하")).isEmpty();
    }

    @Test
    void twoDigitYearWithMarkOrWeekday() {
        assertThat(parser.parse("서류 제출 마감 : `26.10.19(월)")).singleElement()
                .satisfies(r -> assertThat(r.end()).isEqualTo(LocalDate.of(2026, 10, 19)));
        assertThat(parser.parse("서류전형 : 26.10.26 (월) ~ 26.11.08 (일)")).singleElement().satisfies(r -> {
            assertThat(r.start()).isEqualTo(LocalDate.of(2026, 10, 26));
            assertThat(r.end()).isEqualTo(LocalDate.of(2026, 11, 8));
        });
        // 표시도 요일도 없는 두 자리 숫자 셋은 버전 번호일 수 있어 읽지 않는다.
        assertThat(parser.parse("v26.10.19 배포")).isEmpty();
    }

    @Test
    void wordAfterAFullDateIsNotANumericTail() {
        // "까지" 뒤의 "학점교류"를 "2.5 학점"의 꼬리로 보면 마감을 버린다.
        assertThat(parser.parse("2026. 10. 30.(금) 15:00까지 학점교류 학점인정 신청서 제출")).singleElement()
                .satisfies(r -> assertThat(r.end()).isEqualTo(LocalDate.of(2026, 10, 30)));
    }

    @Test
    void startOnlyDateIsNotADeadline() {
        assertThat(parser.parse("신청기간: 10.26(월) 09:00부터 선착순 마감")).isEmpty();
        assertThat(parser.parse("신청기간 : 2026.10.26.(월) 오전 9시 ~ (멘토링 별 선착순 마감)")).isEmpty();
        // 끝 날짜가 이어지면 범위다.
        assertThat(parser.parse("10. 1.(수) 오전 9시 ~ 10. 5.(일)")).hasSize(2);
    }

    @Test
    void weekOfMonthIsNotADay() {
        assertThat(parser.parse("장학금 지급 2026. 11. 1주 예정")).isEmpty();
        assertThat(parser.parse("11월 2주차 발표")).isEmpty();
    }

    @Test
    void twentiethCenturyYearIsReadAsWritten() {
        // "10월 15일"만 떼어 올해로 읽으면 출생일 조건이 마감처럼 보인다.
        List<DateRange> r = parser.parse("만 30세 미만(1996년 10월 15일 이후 출생자)");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).end()).isEqualTo(LocalDate.of(1996, 10, 15));
    }

    @Test
    void reversedRangeIsRejected() {
        assertThat(parser.parse("10. 30.(금) ~ 10. 1.(목)")).isEmpty();
    }

    @Test
    void multipleLinesYieldMultipleRanges() {
        List<DateRange> r = parser.parse("2026. 8. 31.(월) 09:00 ~ 17:00 2026. 9. 1.(화) 09:00 ~ 17:00 2026. 9. 4.(금) 09:00 ~ 17:00");
        assertThat(r).hasSize(3);
        assertThat(r.get(2).end()).isEqualTo(LocalDate.of(2026, 9, 4));
    }
}
