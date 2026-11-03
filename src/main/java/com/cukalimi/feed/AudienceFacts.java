package com.cukalimi.feed;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * AI가 공지 글에서 읽은 대상 조건 (ADR 013). AI는 조건을 옮겨 적기만 하고, 내 프로필과 맞는지는 {@link #reasonFor}가 코드로 판단한다.
 * 비어 있는 목록은 그 조건이 없다는 뜻이다. 모두 비어 있으면 모든 재학생 대상이라 "내 것"으로 따로 올리지 않는다.
 */
public record AudienceFacts(
        @JsonPropertyDescription("지원할 수 있는 학년. 졸업예정자는 4, 신입생은 1. 학기 조건이 있으면 비우고 semesters에 적는다. 학년 제한이 없으면 빈 목록")
        List<Integer> grades,
        @JsonPropertyDescription("학기로 적힌 조건일 때 지금 몇 번째 학기인 학생이 지원할 수 있는지(1~8, 9는 초과학기). 없으면 빈 목록")
        List<Integer> semesters,
        @JsonPropertyDescription("지원할 수 있는 학과·학부·전공 이름을 공지에 적힌 그대로. 제한이 없으면 빈 목록")
        List<String> depts,
        @JsonPropertyDescription("본인이나 부모의 주소·출신 지역 조건. '전라남도 순천시'나 '서울특별시'처럼 시·도 정식 이름으로 시작. 없으면 빈 목록")
        List<String> regions,
        @JsonPropertyDescription("학년·학과·지역 말고 지원을 가르는 조건의 종류. 없으면 빈 목록")
        List<Blocker> blockers,
        @JsonPropertyDescription("근거가 된 원문 문장 하나. 조건이 없으면 빈 문자열")
        String evidence) {

    /**
     * INCOME 소득 분위·기초생활수급 등, GPA 직전 학기 성적·이수 학점, GROUP 특정 계층·신분(장애, 다문화, 보훈, 북한이탈, 외국인, 편입생, 휴학생 등),
     * COURSE 특정 과목 수강생, IMAGE_ONLY 조건이 그림이나 첨부에만 있다고 적힘, OTHER 그 밖의 조건.
     */
    public enum Blocker {
        INCOME, GPA, GROUP, COURSE, IMAGE_ONLY, OTHER
    }

    /**
     * 이것이 있으면 칠하지 않는다. 특정 계층·과목 대상은 대부분의 학생이 해당하지 않는다.
     * 소득·성적·그 밖의 조건은 학생 본인이 판단할 몫이라 칠하되 이유에 "자격 조건은 원문 확인"을 붙인다 (ADR 008).
     */
    static final Set<Blocker> BLOCKING = EnumSet.of(Blocker.GROUP, Blocker.COURSE);

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 학기 조건이 없는 읽기. */
    public AudienceFacts(List<Integer> grades, List<String> depts, List<String> regions, List<Blocker> blockers, String evidence) {
        this(grades, List.of(), depts, regions, blockers, evidence);
    }

    /**
     * 목록을 정렬하고 중복과 빈 값을 지운다. 전 학년이나 정규 학기(1~8) 전부면 그 조건이 없는 것이다.
     * 두 번 읽은 결과를 비교하고 저장할 때 쓴다.
     */
    public AudienceFacts normalized() {
        List<Integer> g = sorted(grades == null ? List.of() : grades.stream().filter(x -> x != null && x >= 1 && x <= 4).toList());
        List<Integer> s = sorted(semesters == null ? List.of() : semesters.stream().filter(x -> x != null && x >= 1 && x <= 9).toList());
        boolean allRegular = s.containsAll(List.of(1, 2, 3, 4, 5, 6, 7, 8));
        return new AudienceFacts(
                g.size() == 4 ? List.of() : g,
                allRegular ? List.of() : s,
                sorted(clean(depts)),
                // "영주시"나 "전남"처럼 줄여 적어도 목록의 이름으로 맞춘다. 맞출 수 없으면 그대로 두고, 비교에서 맞지 않는 지역이 된다.
                sorted(clean(regions).stream().map(r -> Regions.parseLoose(r).map(Regions.Region::label).orElse(r)).toList()),
                blockers == null ? List.of() : List.copyOf(blockers.stream().filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(Blocker.class)))),
                evidence == null ? "" : evidence.trim());
    }

    /**
     * 두 읽기가 같은 판단으로 이어지면 같은 읽기다. 학년·학과·지역과 칠하지 않게 하는 조건(계층·과목)이 같고,
     * 나머지 조건은 있는지 없는지만 같으면 된다. 소득과 성적을 서로 다르게 나눠도 칠하는 결정과 이유 문구는 같다.
     */
    public boolean sameConditions(AudienceFacts other) {
        AudienceFacts a = normalized();
        AudienceFacts b = other.normalized();
        return a.grades.equals(b.grades) && a.semesters.equals(b.semesters) && a.depts.equals(b.depts) && a.regions.equals(b.regions)
                && a.blocking().equals(b.blocking()) && a.blockers.isEmpty() == b.blockers.isEmpty();
    }

    private Set<Blocker> blocking() {
        Set<Blocker> s = EnumSet.noneOf(Blocker.class);
        blockers.stream().filter(BLOCKING::contains).forEach(s::add);
        return s;
    }

    public boolean restricted() {
        return !grades.isEmpty() || !semesters.isEmpty() || !depts.isEmpty() || !regions.isEmpty();
    }

    /**
     * 이 공지가 이 학생의 것이라는 이유. 아니면 null. 적힌 조건이 하나라도 프로필과 맞지 않거나 프로필로 알 수 없으면 null이다.
     * 잘못 칠하는 것이 놓치는 것보다 해롭다.
     */
    public String reasonFor(Feed.Viewer viewer) {
        AudienceFacts f = normalized();
        if (!f.restricted() || f.blockers.stream().anyMatch(BLOCKING::contains)) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (!f.regions.isEmpty()) {
            if (viewer.home() == null) {
                return null;
            }
            Optional<Regions.Region> hit = f.regions.stream().map(Regions::parseLoose).flatMap(Optional::stream)
                    .filter(r -> r.covers(viewer.home())).findFirst();
            if (hit.isEmpty()) {
                return null;
            }
            parts.add(hit.get().label() + " 연고");
        }
        if (!f.grades.isEmpty()) {
            if (viewer.grade() == null || !f.grades.contains(viewer.grade())) {
                return null;
            }
            parts.add(GradeTag.label(new TreeSet<>(f.grades)));
        }
        if (!f.semesters.isEmpty()) {
            if (viewer.semester() == null || !f.semesters.contains(viewer.semester())) {
                return null;
            }
            parts.add(semesterLabel(f.semesters));
        }
        if (!f.depts.isEmpty()) {
            Optional<String> dept = f.depts.stream()
                    .filter(d -> sameDept(d, viewer.major()) || sameDept(d, viewer.secondMajor())).findFirst();
            if (dept.isEmpty()) {
                return null;
            }
            parts.add(dept.get());
        }
        String reason = String.join("·", parts) + " 대상";
        return f.blockers.isEmpty() ? reason : reason + ", 자격 조건은 원문 확인";
    }

    /** "5~7학기", "2학기", 초과학기는 "초과학기"로 적는다. 이어지지 않으면 점으로 잇는다. */
    static String semesterLabel(List<Integer> semesters) {
        List<String> names = semesters.stream().map(s -> s == 9 ? "초과학기" : s + "학기").toList();
        int first = semesters.get(0);
        int last = semesters.get(semesters.size() - 1);
        if (semesters.size() > 1 && last <= 8 && last - first + 1 == semesters.size()) {
            return first + "~" + last + "학기";
        }
        return String.join("·", names);
    }

    /** "컴퓨터정보공학부"와 "컴퓨터정보공학", "경영학과"와 "경영학부"를 같은 곳으로 본다. 끝말을 뗀 이름이 두 글자보다 짧으면 맞추지 않는다. */
    static boolean sameDept(String noticeDept, String profileDept) {
        String a = stem(noticeDept);
        String b = stem(profileDept);
        if (a.length() < 2 || b.length() < 2) {
            return false;
        }
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private static String stem(String dept) {
        return dept == null ? "" : dept.replaceAll("\\s+", "").replaceAll("(전공|부|과)$", "");
    }

    public String toJson() {
        try {
            return JSON.writeValueAsString(normalized());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 저장된 값. 비었거나 읽을 수 없으면 null이라 정규식 결과를 쓴다. */
    public static AudienceFacts fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, AudienceFacts.class).normalized();
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static List<String> clean(List<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static <T extends Comparable<T>> List<T> sorted(List<T> values) {
        return List.copyOf(new TreeSet<>(values));
    }
}
