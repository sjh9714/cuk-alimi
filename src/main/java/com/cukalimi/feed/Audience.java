package com.cukalimi.feed;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공지의 대상 범위. 자격을 판정하지 않는다. 제목에 드러난 범위(지역·특정 계층·특정 분야·결과 발표)만 나누고,
 * 본문의 자격 문단은 해석하지 않고 그대로 발췌한다 (ADR 003 규칙 6, ADR 009).
 *
 * @param scope   GENERAL(제한이 제목에 드러나지 않음), REGION, GROUP, FIELD, RESULT(결과 발표라 할 일이 없음)
 * @param label   화면에 붙는 짧은 말. "전라남도 순천시 연고", "북한이탈청소년"
 * @param regions REGION일 때 지역 후보
 * @param excerpt 본문의 지원자격·신청대상 문단 원문. 없으면 null
 * @param grade   제목과 자격 문단에 적힌 학년 범위. 범위와 따로 붙는다
 */
public record Audience(String scope, String label, List<Regions.Region> regions, String excerpt, GradeTag grade) {

    public static final String GENERAL = "GENERAL";
    public static final String REGION = "REGION";
    public static final String GROUP = "GROUP";
    public static final String FIELD = "FIELD";
    public static final String RESULT = "RESULT";
    public static final List<String> SCOPES = List.of(GENERAL, REGION, GROUP, FIELD, RESULT);

    private static final Pattern RESULT_TITLE = Pattern.compile("(선발|심사|합격|최종)\\s*(결과|자\\s*발표|자\\s*명단)|합격자|결과\\s*(안내|발표|공지)");

    /** 제목에 이 말이 있으면 특정 계층 대상이다. 화면에는 찾은 말을 그대로 붙인다. */
    private static final Pattern GROUP_TITLE = Pattern.compile(
            "북한이탈\\S*|탈북\\S*|새터민|다문화\\S*|보훈\\S*|국가유공자\\S*|장애\\S*|한부모\\S*|선원가족|농어촌\\S*|농촌|어업인\\S*"
                    + "|고졸 후학습자|자립준비\\S*|보호종료\\S*|해외교환|교환학생|외국인 유학생|유학생");

    /** 특정 분야·전공 대상. "AI·ICT 분야", "의생명과학분야". */
    private static final Pattern FIELD_TITLE = Pattern.compile("([A-Za-z가-힣·]+)\\s*분야");

    /** 본문에서 자격 문단이 시작하는 말. */
    private static final Pattern EXCERPT_START = Pattern.compile(
            "(?:지원|신청|선발|모집|추천|응모)\\s*(?:자격|대상)(?![가-힣])(?:\\s*\\([^)]{0,20}\\))?\\s*[:：]?\\s*-?\\s*"
                    + "|자격\\s*요건\\s*[:：]?\\s*|(?<![가-힣])대\\s*상\\s*[:：]\\s*");

    /** 자격 문단이 끝나는 자리. 다음 번호 항목이나 기간·금액·서류 같은 다음 머리말. */
    private static final Pattern EXCERPT_END = Pattern.compile(
            "\\s\\d{1,2}\\.\\s(?=[가-힣])|[●□■◆▶◎○]|\\[|\\s-?\\s*[가-힣]{1,4}\\s*기간\\s*[:：]"
                    + "|\\s(?:신청|접수|모집|지원|선발)\\s*(?:기간|방법|절차|일정|인원|규모|금액|내용)"
                    + "|\\s-?\\s*(?:장학\\s*금액|장학금\\s*(?:지원|성격)|지원\\s*금액|지급|제출\\s*서류|문의)");

    private static final int EXCERPT_MAX = 150;

    /** 학사 공지는 학교 안 행정이라 지역·계층 장학 규칙을 대지 않는다. 결과 발표만 거른다. */
    public static Audience classify(String category, String title, String body) {
        String excerpt = excerpt(body);
        GradeTag grade = GradeTag.of(title, excerpt);
        if (RESULT_TITLE.matcher(title).find()) {
            return new Audience(RESULT, "결과 발표", List.of(), excerpt, grade);
        }
        if (!"장학".equals(category)) {
            return new Audience(GENERAL, null, List.of(), excerpt, grade);
        }
        List<Regions.Region> regions = Regions.inTitle(title);
        if (regions.isEmpty()) {
            regions = Regions.inAudienceSentence(excerpt);
        }
        if (!regions.isEmpty()) {
            return new Audience(REGION, regions.get(0).label() + " 연고", regions, excerpt, grade);
        }
        Matcher group = GROUP_TITLE.matcher(title);
        if (group.find()) {
            return new Audience(GROUP, group.group().replaceAll("(장학생|장학금|장학사업)$", ""), List.of(), excerpt, grade);
        }
        Matcher field = FIELD_TITLE.matcher(title);
        if (field.find()) {
            return new Audience(FIELD, field.group(1) + " 분야", List.of(), excerpt, grade);
        }
        return new Audience(GENERAL, null, List.of(), excerpt, grade);
    }

    /** 공지의 대상 범위. 운영자가 고친 값이 있으면 그것이 이긴다. */
    public static Audience of(com.cukalimi.domain.Notice n) {
        return classify(n.getCategory(), n.getTitle(), n.getBodyText()).overriddenBy(n.getAudienceScope(), n.getAudienceLabel());
    }

    /** 운영자가 고친 범위가 있으면 그것을 쓴다. 발췌문과 학년은 그대로 둔다. */
    public Audience overriddenBy(String scope, String label) {
        if (scope == null || scope.isBlank()) {
            return this;
        }
        return new Audience(scope, label == null || label.isBlank() ? null : label.trim(),
                REGION.equals(scope) ? regions : List.of(), excerpt, grade);
    }

    /** 자격 문단을 원문 그대로 짧게 자른다. 고쳐 쓰지 않는다. */
    static String excerpt(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        Matcher start = EXCERPT_START.matcher(body);
        if (!start.find()) {
            return null;
        }
        String rest = body.substring(start.end());
        Matcher end = EXCERPT_END.matcher(rest);
        if (end.find()) {
            rest = rest.substring(0, end.start());
        }
        rest = rest.replaceAll("\\s+", " ").replaceAll("^[\\s\\-:：·]+", "").replaceAll("[\\s\\-,(:：]+$", "").trim();
        if (rest.length() < 4) {
            return null;
        }
        if (rest.length() > EXCERPT_MAX) {
            int cut = rest.lastIndexOf(' ', EXCERPT_MAX);
            rest = rest.substring(0, cut > EXCERPT_MAX / 2 ? cut : EXCERPT_MAX).replaceAll("[\\s\\-,(]+$", "") + "…";
        }
        return rest;
    }
}
