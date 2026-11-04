package com.cukalimi.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 주제는 제목으로 정하고 선언 순서가 우선순위다 (ADR 014). 제목은 학교 게시판의 실제 제목이다. */
class TopicTest {

    @Test
    void 순서가_겹치는_제목을_가른다() {
        assertThat(Topic.of("[학사행정팀] 2026학년도 겨울계절학기 서울 및 경인지역 대학교 학점교류 안내")).isEqualTo(Topic.EXCHANGE);
        assertThat(Topic.of("2026학년도 겨울 계절학기 수강신청 안내")).isEqualTo(Topic.SEASONAL);
        assertThat(Topic.of("[학생지원팀](한국장학재단) 2026학년도 1학기 국가근로장학금 2차 신청 안내")).isEqualTo(Topic.WORKSTUDY);
        assertThat(Topic.of("[학생지원팀][한국장학재단]2026년 1학기 국가장학금 2차 신청 안내")).isEqualTo(Topic.SCHOLARSHIP);
        assertThat(Topic.of("[중요] 2026-2학기 졸업시험 신청 및 대체요건 제출 안내")).isEqualTo(Topic.GRADUATION);
        assertThat(Topic.of("[학사지원팀] 2026학년도 2학기 복학 및 일반휴학 신청 안내")).isEqualTo(Topic.COURSE);
        assertThat(Topic.of("[대플-추천채용] 키엔스코리아(주) - 컨설팅 세일즈 신입 채용")).isEqualTo(Topic.CAREER);
        assertThat(Topic.of("「2026 CUK AI Startup Challenge – AI 융합 해커톤」 참가자 모집")).isEqualTo(Topic.EXTRACURRICULAR);
        assertThat(Topic.of("27년 전기 1차 대학원 신입생 모집 안내")).isEqualTo(Topic.GRAD);
        assertThat(Topic.of("[학사지원팀]2025학년도 동계방학 중 단축근무 실시 안내")).isEqualTo(Topic.OTHER);
    }

    @Test
    void 관심_주제_목록을_읽고_모르는_키는_받지_않는다() {
        assertThat(Topic.parse(null)).isEqualTo(Topic.DEFAULTS);
        assertThat(Topic.parse("")).isEmpty();
        assertThat(Topic.parse("course,exchange")).containsExactlyInAnyOrder(Topic.COURSE, Topic.EXCHANGE);
        assertThatThrownBy(() -> Topic.parse("course,nope")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Topic.DEFAULTS).doesNotContain(Topic.EXCHANGE, Topic.CAREER);
    }
}
