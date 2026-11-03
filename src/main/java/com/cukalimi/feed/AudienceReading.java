package com.cukalimi.feed;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

/**
 * AI가 공지 글에서 옮겨 적은 그대로의 대상 조건 (ADR 013). 학기 조건은 계산하지 않고 문장의 종류와 숫자만 적는다.
 * 몇 번째 학기 학생이 해당하는지는 {@link #toFacts()}가 코드로 센다. 2026-11-03 측정에서 AI가 틀린 것이 모두 이 계산이었다.
 */
public record AudienceReading(
        @JsonPropertyDescription("학년으로만 적힌 조건의 학년 목록. 졸업예정자는 4, 신입생은 1, '1학년 이상 수료'는 2,3,4. 없으면 빈 목록")
        List<Integer> grades,
        @JsonPropertyDescription("학기로 적힌 조건. 문장마다 하나씩, 종류와 숫자만. 없으면 빈 목록")
        List<SemesterRule> semesterRules,
        @JsonPropertyDescription("지원할 수 있는 학과·학부·전공 이름을 공지에 적힌 그대로. 제한이 없으면 빈 목록")
        List<String> depts,
        @JsonPropertyDescription("본인이나 부모의 주소·출신 지역 조건. '전라남도 순천시'나 '서울특별시'처럼 시·도 정식 이름으로 시작. 없으면 빈 목록")
        List<String> regions,
        @JsonPropertyDescription("학년·학기·학과·지역 말고 지원을 가르는 조건의 종류. 없으면 빈 목록")
        List<AudienceFacts.Blocker> blockers,
        @JsonPropertyDescription("근거가 된 원문 문장 하나. 조건이 없으면 빈 문자열")
        String evidence) {

    /**
     * COMPLETED_SEMESTERS_AT_LEAST "N학기 이상 이수/수료", COMPLETED_GRADES_AT_LEAST "N학년 이상 수료"(학기 조건과 함께 있을 때),
     * ENROLLED_AT_LEAST "N학기 이상 재학생", NOT_ENROLLED_AT_LEAST "N학기 이상 재학 중인 자는 불가",
     * NOT_FINAL_SEMESTER "마지막 학기·졸업예정자는 불가", ENTERING "g학년 h학기 진학 예정자"(value=g, half=h),
     * WITHIN_REGULAR "정규학기(8학기) 이내", REMAINING_AT_LEAST "졸업까지 N개 학기 이상 남은 자".
     */
    public enum Kind {
        COMPLETED_SEMESTERS_AT_LEAST, COMPLETED_GRADES_AT_LEAST, ENROLLED_AT_LEAST, NOT_ENROLLED_AT_LEAST,
        NOT_FINAL_SEMESTER, ENTERING, WITHIN_REGULAR, REMAINING_AT_LEAST
    }

    public record SemesterRule(
            @JsonPropertyDescription("조건 문장의 종류")
            Kind kind,
            @JsonPropertyDescription("문장에 적힌 숫자. ENTERING이면 학년. 숫자가 없는 종류는 0")
            int value,
            @JsonPropertyDescription("ENTERING일 때만 학기(1 또는 2). 그 밖에는 0")
            int half) {

        /** 지금 c번째 학기(1~9, 9는 초과학기)인 학생이 이 조건에 맞는가. 알 수 없는 값이면 모두 맞다고 본다. */
        IntPredicate allows() {
            return switch (kind) {
                case COMPLETED_SEMESTERS_AT_LEAST -> c -> c >= value + 1;
                case COMPLETED_GRADES_AT_LEAST -> c -> c >= 2 * value + 1;
                case ENROLLED_AT_LEAST -> c -> c >= value;
                case NOT_ENROLLED_AT_LEAST -> c -> value <= 0 || c <= value - 1;
                case NOT_FINAL_SEMESTER -> c -> c <= 7;
                case ENTERING -> {
                    int now = (value - 1) * 2 + (half == 2 ? 2 : 1) - 1;
                    yield value < 1 || value > 4 ? c -> true : c -> c == now;
                }
                case WITHIN_REGULAR -> c -> c <= 8;
                case REMAINING_AT_LEAST -> c -> value <= 0 || c <= 9 - value;
            };
        }
    }

    /**
     * 비교에 쓰는 조건으로 바꾼다. 학기 조건이 있으면 학년 조건도 학기(학년 g는 2g-1, 2g번째, 4학년은 초과학기 포함)로 바꿔 함께 따진다.
     * 학기 계산은 여기서만 한다.
     */
    public AudienceFacts toFacts() {
        List<SemesterRule> rules = semesterRules == null ? List.of() : semesterRules.stream().filter(Objects::nonNull)
                .filter(r -> r.kind() != null).toList();
        List<Integer> g = grades == null ? List.of() : grades.stream().filter(x -> x != null && x >= 1 && x <= 4).toList();
        if (rules.isEmpty()) {
            return new AudienceFacts(g, List.of(), depts, regions, blockers, evidence).normalized();
        }
        TreeSet<Integer> semesters = IntStream.rangeClosed(1, 9).boxed()
                .filter(c -> g.isEmpty() || g.contains(Math.min(4, (c + 1) / 2)))
                .filter(c -> rules.stream().allMatch(r -> r.allows().test(c)))
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        // 조건을 모두 맞추는 학기가 없으면 읽기가 잘못된 것이다. 아무에게도 칠하지 않도록 학과·지역 조건도 남기지 않고 그 밖의 조건으로 둔다.
        if (semesters.isEmpty()) {
            return new AudienceFacts(List.of(), List.of(), List.of(), List.of(), List.of(AudienceFacts.Blocker.OTHER), evidence)
                    .normalized();
        }
        return new AudienceFacts(List.of(), List.copyOf(semesters), depts, regions, blockers, evidence).normalized();
    }
}
