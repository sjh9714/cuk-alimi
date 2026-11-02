package com.cukalimi.feed;

import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 제목과 자격 문단에 적힌 학년 범위. "(2~4학년)", "2학년 이상 재학생", "졸업대상자", "신입생"처럼 글자로 드러난 것만 읽는다.
 * 내 학년과 맞으면 위로 올리는 데만 쓰고 숨기는 데는 쓰지 않는다 (ADR 008). 애매하면 붙이지 않는다.
 *
 * @param grades 해당 학년. 비어 있으면 모른다(또는 전 학년)
 * @param label  화면에 붙는 짧은 말. "2~4학년", "졸업예정자"
 */
public record GradeTag(List<Integer> grades, String label) {

    public static final GradeTag NONE = new GradeTag(List.of(), null);

    private static final Pattern RANGE = Pattern.compile("(?<!\\d)([1-4])\\s*[~∼〜～\\-–]\\s*([1-4])\\s*학년(?!도)");
    /** "1학년 이상 수료한 학생"은 2학년 이상이라는 뜻이라 맞추지 않는다. */
    private static final Pattern AT_LEAST = Pattern.compile("(?<![\\d~∼〜～\\-–])([1-4])\\s*학년\\s*이상(?!\\s*수료)");
    /** "2학년 1학기 진학 예정자"는 지금 1학년이다. 해석하지 않고 건너뛴다. */
    private static final Pattern SINGLE = Pattern.compile(
            "(?<![\\d~∼〜～\\-–])([1-4])\\s*학년(?!도|\\s*이상|\\s*(?:[1-2]\\s*학기\\s*)?(?:진학|진급|수료))");
    private static final Pattern GRADUATING = Pattern.compile("졸업\\s*(?:대상|예정)");
    private static final Pattern FRESHMAN = Pattern.compile("신입생|새내기");
    /** 바로 뒤에 이 말이 오면 그 학년을 빼라는 뜻이다. "졸업예정자 혹은 8학기 이상 재학중인 자는 지원 불가". */
    /** 학년 표기 바로 뒤의 부정어. 마침표나 여는 괄호에서 멈춘다. "2~4학년 재학생(단, 휴학생 제외)"의 제외는 학년을 지우지 않는다. */
    private static final Pattern NEGATION = Pattern.compile("^[^.。\\n(（]{0,30}?(?:불가|제외|안\\s*됨|할\\s*수\\s*없)");

    public static GradeTag of(String... texts) {
        TreeSet<Integer> grades = new TreeSet<>();
        boolean graduating = false;
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            Matcher m = RANGE.matcher(text);
            while (m.find()) {
                if (!negated(text, m.end())) {
                    for (int g = Integer.parseInt(m.group(1)); g <= Integer.parseInt(m.group(2)); g++) {
                        grades.add(g);
                    }
                }
            }
            m = AT_LEAST.matcher(text);
            while (m.find()) {
                if (!negated(text, m.end())) {
                    for (int g = Integer.parseInt(m.group(1)); g <= 4; g++) {
                        grades.add(g);
                    }
                }
            }
            m = SINGLE.matcher(text);
            while (m.find()) {
                if (!negated(text, m.end())) {
                    grades.add(Integer.parseInt(m.group(1)));
                }
            }
            m = GRADUATING.matcher(text);
            while (m.find()) {
                if (!negated(text, m.end())) {
                    grades.add(4);
                    graduating = true;
                }
            }
            m = FRESHMAN.matcher(text);
            while (m.find()) {
                if (!negated(text, m.end())) {
                    grades.add(1);
                }
            }
        }
        if (grades.isEmpty() || grades.size() == 4) {
            return NONE;
        }
        return new GradeTag(List.copyOf(grades), graduating && grades.size() == 1 ? "졸업예정자" : label(grades));
    }

    public boolean covers(Integer grade) {
        return grade != null && grades.contains(grade);
    }

    private static boolean negated(String text, int end) {
        return NEGATION.matcher(text.substring(end)).find();
    }

    private static String label(TreeSet<Integer> grades) {
        if (grades.last() - grades.first() + 1 == grades.size() && grades.size() > 1) {
            return grades.first() + "~" + grades.last() + "학년";
        }
        return String.join("·", grades.stream().map(String::valueOf).toList()) + "학년";
    }
}
