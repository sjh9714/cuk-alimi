package com.cukalimi.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** 2026-10-26에 실제로 수집한 공지 62건(장학 31, 학사 31)으로 대상 범위 규칙을 고정한다. 연락처는 지웠다. */
class AudienceTest {

    static final Set<Long> REGION = Set.of(275600L, 276061L, 276187L, 276440L, 276477L, 276482L, 276487L, 276597L,
            276604L, 276606L, 276607L, 276635L, 276848L);
    static final Set<Long> GROUP = Set.of(275978L, 276150L, 276341L, 276506L, 276603L);
    static final Set<Long> FIELD = Set.of(276474L);
    static final Set<Long> RESULT = Set.of(276423L);

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> notices() throws Exception {
        return new ObjectMapper().readValue(AudienceTest.class.getResourceAsStream("/fixtures/notices-2026-10-26.json"),
                List.class);
    }

    static Audience classify(Map<String, Object> n) {
        return Audience.classify((String) n.get("category"), (String) n.get("title"), (String) n.get("body"));
    }

    @Test
    void everyRealNoticeLandsInTheExpectedScope() throws Exception {
        Map<Long, String> actual = new TreeMap<>();
        Map<Long, String> expected = new TreeMap<>();
        for (var n : notices()) {
            long no = ((Number) n.get("articleNo")).longValue();
            actual.put(no, classify(n).scope());
            expected.put(no, REGION.contains(no) ? Audience.REGION : GROUP.contains(no) ? Audience.GROUP
                    : FIELD.contains(no) ? Audience.FIELD : RESULT.contains(no) ? Audience.RESULT : Audience.GENERAL);
        }
        assertThat(actual).hasSize(62).isEqualTo(expected);
    }

    @Test
    void regionLabelsNameTheRightPlace() throws Exception {
        Map<Long, String> labels = new TreeMap<>();
        for (var n : notices()) {
            long no = ((Number) n.get("articleNo")).longValue();
            if (REGION.contains(no)) {
                labels.put(no, classify(n).label());
            }
        }
        assertThat(labels).containsEntry(276440L, "서울특별시 마포구 연고")      // 마포인재육성장학재단 (구 없이 줄임)
                .containsEntry(276604L, "전라남도 연고")                         // 전남인재육성
                .containsEntry(275600L, "대전광역시 연고")                       // 대전청년내일재단
                .containsEntry(276061L, "강원특별자치도 춘천시 연고")            // 춘천인재육성
                .containsEntry(276597L, "인천광역시 계양구 연고")
                .containsEntry(276477L, "서울특별시 서초구 연고");               // 제목엔 없고 대상 문장에만 있다
    }

    @Test
    void excerptIsTheOriginalSentenceNotARewrite() throws Exception {
        Map<Long, String> excerpts = new TreeMap<>();
        for (var n : notices()) {
            excerpts.put(((Number) n.get("articleNo")).longValue(), classify(n).excerpt());
        }
        assertThat(excerpts.get(276477L)).isEqualTo("접수 마감일(2026.10.8.) 기준 부·모 또는 본인이 1년 이상 서초구에 거주중인 자");
        assertThat(excerpts.get(276604L)).isEqualTo("장학금별 신청자격을 충족하는 국내 대학 재학생");
        // "추천대상자를 선발하며"의 "추천대상"은 머리말이 아니다.
        assertThat(excerpts.get(276941L)).startsWith("대한민국 국적의 2026학년도 2학기 정규학기 재학생").endsWith("…");
        // 날짜 "2025. 12. 31."의 "12. "를 다음 번호 항목으로 보면 안 된다.
        assertThat(excerpts.get(276482L)).startsWith("2025. 12. 31.부터 공고일 현재까지 진도군에 주민등록");
        assertThat(excerpts.values().stream().filter(e -> e != null)).allMatch(e -> e.length() <= 151);
    }

    /** 학년은 제목과 자격 문단에 글자로 적힌 것만 붙는다. 62건 중 이것들뿐이다. */
    @Test
    void gradeTagsComeOnlyFromWrittenRanges() throws Exception {
        Map<Long, String> tags = new TreeMap<>();
        for (var n : notices()) {
            GradeTag tag = classify(n).grade();
            if (tag != GradeTag.NONE) {
                tags.put(((Number) n.get("articleNo")).longValue(), tag.label() + " " + tag.grades());
            }
        }
        assertThat(tags).isEqualTo(Map.of(
                274706L, "졸업예정자 [4]",      // 2026년 8월 졸업대상자 융복합트랙 이수여부 확인
                275055L, "2~4학년 [2, 3, 4]",   // (2~4학년) 부전공(2차) 신청
                275089L, "졸업예정자 [4]",      // 졸업대상자 예비 졸업사정
                276187L, "1학년 [1]"));         // 논산시장학회. 요건을 모두 충족하는 학생 가. 신입생 (지역 장학이라 올리지는 않는다)
    }

    @Test
    void ambiguousGradeWordsAreLeftAlone() {
        assertThat(GradeTag.of("4년제 대학 2학년 1학기 진학 예정자 중 성적 및 소득 요건")).isEqualTo(GradeTag.NONE);
        assertThat(GradeTag.of("1학년 이상 수료한 학생")).isEqualTo(GradeTag.NONE);
        assertThat(GradeTag.of("4학기 이상 이수한 자(단, 졸업예정자 혹은 8학기 이상 재학중인 자는 지원 불가)")).isEqualTo(GradeTag.NONE);
        assertThat(GradeTag.of("2026학년도 2학기 정규학기 재학생")).isEqualTo(GradeTag.NONE);
        assertThat(GradeTag.of("전 학년 1~4학년")).isEqualTo(GradeTag.NONE);
        assertThat(GradeTag.of("AI·ICT 분야 전공 대학교(학부) 2학년 이상 재학생").label()).isEqualTo("2~4학년");
        assertThat(GradeTag.of("2026년도 국내 정규대학교 신입생").label()).isEqualTo("1학년");
        assertThat(GradeTag.of("신입생 제외")).isEqualTo(GradeTag.NONE);
        // 괄호 속 단서는 앞의 학년을 지우지 않는다.
        assertThat(GradeTag.of("2~4학년 재학생(단, 휴학생 제외)").label()).isEqualTo("2~4학년");
    }

    @Test
    void universityNamesAndAddressesAreNotRegionConditions() {
        assertThat(Regions.inTitle("2026-겨울학기 가톨릭계 대학 학점교류 안내(서강대, 가톨릭관동대, 대구가톨릭대, 부산가톨릭대)")).isEmpty();
        assertThat(Regions.inTitle("2027년도 두산연강재단 대학 장학생 선발 안내")).isEmpty();
        assertThat(Regions.inTitle("<아산사회복지재단> 2027년 의생명과학분야 대학 장학생 선발 안내")).isEmpty();
        assertThat(Regions.inAudienceSentence("우편접수 서울 서초구 남부순환로 2584 교육지원과")).isEmpty();
        assertThat(Audience.classify("학사", "2026학년도 겨울계절학기 서울 및 경인지역 대학교 학점교류 안내", "").scope())
                .isEqualTo(Audience.GENERAL);
    }

    @Test
    void sameNamedDistrictsNeedTheCity() {
        assertThat(Regions.inTitle("부산광역시 중구 장학회 장학생 선발")).containsExactly(new Regions.Region("부산광역시", "중구"));
        assertThat(Regions.inTitle("중구 장학회 장학생 선발")).hasSizeGreaterThan(1);
    }

    @Test
    void regionCoversHome() {
        Regions.Region home = Regions.parse("전라남도 순천시").orElseThrow();
        assertThat(new Regions.Region("전라남도", null).covers(home)).isTrue();
        assertThat(new Regions.Region("전라남도", "순천시").covers(home)).isTrue();
        assertThat(new Regions.Region("전라남도", "여수시").covers(home)).isFalse();
        assertThat(Regions.parse("전라남도 강남구")).isEmpty();
        assertThat(Regions.parse("세종특별자치시")).contains(new Regions.Region("세종특별자치시", null));
    }

    @Test
    void operatorOverrideWins() {
        Audience auto = Audience.classify("장학", "2026년 하반기 은평구민장학재단 장학생 선발 안내", "");
        assertThat(auto.scope()).isEqualTo(Audience.REGION);
        Audience fixed = auto.overriddenBy(Audience.GENERAL, null);
        assertThat(fixed.scope()).isEqualTo(Audience.GENERAL);
        assertThat(fixed.regions()).isEmpty();
        assertThat(auto.overriddenBy(null, "무시")).isSameAs(auto);
    }
}
