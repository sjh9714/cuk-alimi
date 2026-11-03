package com.cukalimi.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.cukalimi.feed.AudienceFacts.Blocker;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AI가 읽은 대상 조건과 프로필을 코드로 맞춘다 (ADR 013). */
class AudienceFactsTest {

    static AudienceFacts facts(List<Integer> grades, List<String> depts, List<String> regions, List<Blocker> blockers) {
        return new AudienceFacts(grades, depts, regions, blockers, "근거");
    }

    static final Feed.Viewer THIRD_CS_SUNCHEON = new Feed.Viewer(3, "컴퓨터정보공학부", "경영학과",
            Regions.parse("전라남도 순천시").orElseThrow());

    @Test
    void 조건이_모두_맞으면_이유를_붙인다() {
        AudienceFacts f = facts(List.of(3, 4), List.of("컴퓨터정보공학부"), List.of(), List.of());
        assertThat(f.reasonFor(THIRD_CS_SUNCHEON)).isEqualTo("3~4학년·컴퓨터정보공학부 대상");
    }

    @Test
    void 조건이_하나라도_맞지_않거나_모르면_칠하지_않는다() {
        assertThat(facts(List.of(1), List.of(), List.of(), List.of()).reasonFor(THIRD_CS_SUNCHEON)).isNull();
        assertThat(facts(List.of(3), List.of("간호학과"), List.of(), List.of()).reasonFor(THIRD_CS_SUNCHEON)).isNull();
        assertThat(facts(List.of(3), List.of(), List.of(), List.of()).reasonFor(Feed.Viewer.ANONYMOUS)).isNull();
        assertThat(facts(List.of(), List.of(), List.of("서울특별시"), List.of()).reasonFor(THIRD_CS_SUNCHEON)).isNull();
    }

    @Test
    void 학기로_적힌_조건은_이번_학기로_맞춘다() {
        AudienceFacts f = new AudienceFacts(List.of(), List.of(5, 6, 7), List.of(), List.of(), List.of(), "");
        assertThat(f.reasonFor(new Feed.Viewer(3, 6, null, null, null))).isEqualTo("5~7학기 대상");
        assertThat(f.reasonFor(new Feed.Viewer(4, 8, null, null, null))).isNull();
        // 학기를 모르면(학년만 있으면) 칠하지 않는다.
        assertThat(f.reasonFor(new Feed.Viewer(3, null, null, null))).isNull();
        // 정규 학기 전부면 조건이 없는 것이다.
        assertThat(new AudienceFacts(List.of(), List.of(1, 2, 3, 4, 5, 6, 7, 8), List.of(), List.of(), List.of(), "")
                .normalized().restricted()).isFalse();
        assertThat(AudienceFacts.semesterLabel(List.of(2))).isEqualTo("2학기");
        assertThat(AudienceFacts.semesterLabel(List.of(8, 9))).isEqualTo("8학기·초과학기");
    }

    @Test
    void 조건이_없으면_모두의_것이라_칠하지_않는다() {
        assertThat(facts(List.of(), List.of(), List.of(), List.of()).reasonFor(THIRD_CS_SUNCHEON)).isNull();
    }

    @Test
    void 시도_단위_지역은_그_안의_시군구를_덮는다() {
        assertThat(facts(List.of(), List.of(), List.of("전라남도"), List.of()).reasonFor(THIRD_CS_SUNCHEON))
                .isEqualTo("전라남도 연고 대상");
        assertThat(facts(List.of(), List.of(), List.of("전라남도 여수시"), List.of()).reasonFor(THIRD_CS_SUNCHEON)).isNull();
        // 줄여 적은 이름도 목록의 이름으로 맞춘다.
        assertThat(facts(List.of(), List.of(), List.of("전남"), List.of()).reasonFor(THIRD_CS_SUNCHEON)).isEqualTo("전라남도 연고 대상");
        assertThat(facts(List.of(), List.of(), List.of("순천시"), List.of()).reasonFor(THIRD_CS_SUNCHEON))
                .isEqualTo("전라남도 순천시 연고 대상");
    }

    @Test
    void 시도_없이_적은_시군구는_하나뿐일_때만_맞춘다() {
        assertThat(Regions.parseLoose("영주시")).contains(new Regions.Region("경상북도", "영주시"));
        assertThat(Regions.parseLoose("서울 은평구")).contains(new Regions.Region("서울특별시", "은평구"));
        assertThat(Regions.parseLoose("중구")).isEmpty();
        assertThat(AudienceFacts.fromJson("{\"regions\":[\"영주시\"]}").regions()).containsExactly("경상북도 영주시");
    }

    @Test
    void 복수전공도_학과로_본다() {
        assertThat(facts(List.of(), List.of("경영학부"), List.of(), List.of()).reasonFor(THIRD_CS_SUNCHEON)).isEqualTo("경영학부 대상");
    }

    @Test
    void 계층_과목_조건은_칠하지_않고_나머지_조건은_원문_확인을_붙인다() {
        assertThat(facts(List.of(3), List.of(), List.of(), List.of(Blocker.GROUP)).reasonFor(THIRD_CS_SUNCHEON)).isNull();
        assertThat(facts(List.of(3), List.of(), List.of(), List.of(Blocker.COURSE)).reasonFor(THIRD_CS_SUNCHEON)).isNull();
        assertThat(facts(List.of(3), List.of(), List.of(), List.of(Blocker.GPA, Blocker.OTHER)).reasonFor(THIRD_CS_SUNCHEON))
                .isEqualTo("3학년 대상, 자격 조건은 원문 확인");
        assertThat(facts(List.of(3), List.of(), List.of(), List.of(Blocker.IMAGE_ONLY)).reasonFor(THIRD_CS_SUNCHEON))
                .isEqualTo("3학년 대상, 자격 조건은 원문 확인");
    }

    @Test
    void 근거_문장만_다르면_같은_읽기다() {
        AudienceFacts a = new AudienceFacts(List.of(4, 3), List.of("경영학과"), List.of(), List.of(Blocker.GPA), "가");
        AudienceFacts b = new AudienceFacts(List.of(3, 4, 4), List.of(" 경영학과 "), null, List.of(Blocker.GPA), "나");
        assertThat(a.sameConditions(b)).isTrue();
        assertThat(a.sameConditions(new AudienceFacts(List.of(3), List.of("경영학과"), List.of(), List.of(Blocker.GPA), "가"))).isFalse();
    }

    @Test
    void 저장한_값을_그대로_읽는다() {
        AudienceFacts a = facts(List.of(2, 3), List.of("국어국문학과"), List.of("서울특별시 은평구"), List.of(Blocker.INCOME));
        assertThat(AudienceFacts.fromJson(a.toJson())).isEqualTo(a.normalized());
        assertThat(AudienceFacts.fromJson("깨진 값")).isNull();
        assertThat(AudienceFacts.fromJson(null)).isNull();
    }

    @Test
    void 학과_이름은_끝말을_떼고_맞춘다() {
        assertThat(AudienceFacts.sameDept("컴퓨터정보공학부", "컴퓨터정보공학")).isTrue();
        assertThat(AudienceFacts.sameDept("간호학과", "간호학과")).isTrue();
        assertThat(AudienceFacts.sameDept("수학과", "경영학과")).isFalse();
        assertThat(AudienceFacts.sameDept("수학과", "수학과")).isTrue();
        assertThat(AudienceFacts.sameDept("경영학부", "경영학과")).isTrue();
        assertThat(AudienceFacts.sameDept("공학", null)).isFalse();
    }
}
