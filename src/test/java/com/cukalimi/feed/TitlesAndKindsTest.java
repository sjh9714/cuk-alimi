package com.cukalimi.feed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TitlesAndKindsTest {

    @Test
    void cleanDropsDepartmentAndTrailingDates() {
        assertThat(Titles.clean("[학생지원팀] 2026년 하반기 은평구민장학재단 장학생 선발 안내(10. 12.(월) ~ 10. 28.(수))"))
                .isEqualTo("2026년 하반기 은평구민장학재단 장학생 선발 안내");
        assertThat(Titles.clean("[학생지원팀] <아산사회복지재단> 2027년도 북한이탈청소년 장학생 선발 안내(~10.26"))
                .isEqualTo("<아산사회복지재단> 2027년도 북한이탈청소년 장학생 선발 안내");
        assertThat(Titles.clean("[학생지원팀][한국장학재단]2026년 2학기 푸른등대 기부장학금(2차) 신청 안내"))
                .isEqualTo("[한국장학재단]2026년 2학기 푸른등대 기부장학금(2차) 신청 안내");
        assertThat(Titles.clean("[학사행정팀] 2026-2학기 전공(전선, 전필) 교과목 변동사항 안내 <2026.09.04. 업데이트>"))
                .isEqualTo("2026-2학기 전공(전선, 전필) 교과목 변동사항 안내");
        assertThat(Titles.clean("﻿[학생지원팀] 2026-2학기 국가근로장학금 추가신청기간 안내(10/21~10/29)"))
                .isEqualTo("2026-2학기 국가근로장학금 추가신청기간 안내");
    }

    @Test
    void cleanKeepsWhatIsNotADate() {
        assertThat(Titles.clean("2026-겨울학기 가톨릭계 대학 학점교류 안내(서강대, 가톨릭관동대)"))
                .isEqualTo("2026-겨울학기 가톨릭계 대학 학점교류 안내(서강대, 가톨릭관동대)");
        assertThat(Titles.clean("[학생지원팀] 2026년 2학기 한국선원복지고용센터 선원가족장학생 선발 안내(~10.30 접수기간 연장)"))
                .isEqualTo("2026년 2학기 한국선원복지고용센터 선원가족장학생 선발 안내 (기간 연장)");
        assertThat(Titles.clean("(2~4학년) 2026학년도 2학기 부전공(2차) 신청/변경 안내"))
                .isEqualTo("(2~4학년) 2026학년도 2학기 부전공(2차) 신청/변경 안내");
    }

    @Test
    void onlyWatchingDatesAreInfo() {
        for (String info : new String[] { "서류 합격자 발표", "온라인 영상 면접", "최종 선발 결과 공개", "대학 선발", "근로기간",
                "계절학기 수업", "가을 학위수여식", "2학기 개시일", "장학증서 수여식", "개설대상과목 공고", "수업기간",
                "장학금 지급", "장학금 지급 예정", "일시", "일 시", "특강 일시", "운영기간", "근무기간", "멘토링 진행기간",
                "일정 · OT (1시간) · 전문강사 진행", "일정 · 기업분석 특강 (2시간)", "과목조회" }) {
            assertThat(ItemKind.of(info)).as(info).isEqualTo(ItemKind.INFO);
        }
        for (String action : new String[] { "신청기간", "트리니티 신청", "원본서류 제출", "재학생 등록기간", "휴학원 제출기간",
                "2학기 성적확인 및 오류정정", "평가기간", "동국대학교 · 서울", "마감 기한", "계절학기 수강신청",
                "2026년 하반기 은평구민장학재단 장학생 선발 안내", "장학금 지급 신청", "접수 일시",
                "결과보고서 제출", "면접 신청", "설명회 참가 신청", "오리엔테이션 신청기간" }) {
            assertThat(ItemKind.of(action)).as(action).isEqualTo(ItemKind.ACTION);
        }
    }
}
