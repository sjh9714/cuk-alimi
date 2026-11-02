package com.cukalimi.feed;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 학교 공지 제목을 목록에서 읽기 좋게 줄인다. 뜻은 바꾸지 않는다. 원문 제목은 링크와 함께 남는다. */
public final class Titles {

    /** [학생지원팀], [학사행정팀], [교무처]처럼 글 쓴 부서. [한국장학재단] 같은 출처는 남긴다. */
    private static final Pattern LEADING_DEPT = Pattern.compile("^\\s*\\[[^\\]]*(팀|처|부|과|실|센터|대학|학부)\\]\\s*");
    /** 끝에 붙은 날짜 괄호. 닫는 괄호가 잘린 것도 있다. "(10. 12.(월) ~ 10. 28.(수))", "(~10.26". */
    private static final Pattern TRAILING_DATE = Pattern.compile("\\s*[(（][^()（）]*\\d[^()（）]*(?:\\([^()]*\\)[^()（）]*)*[)）]?\\s*$");
    private static final Pattern TRAILING_UPDATE = Pattern.compile("\\s*<[^>]*업데이트>\\s*$");
    private static final Pattern INVISIBLE = Pattern.compile("[\\uFEFF\\u200B-\\u200D]");

    private Titles() {
    }

    public static String clean(String title) {
        if (title == null) {
            return "";
        }
        String t = INVISIBLE.matcher(title).replaceAll("");
        Matcher dept;
        while ((dept = LEADING_DEPT.matcher(t)).find()) {
            t = t.substring(dept.end());
        }
        t = TRAILING_UPDATE.matcher(t).replaceAll("");
        Matcher date = TRAILING_DATE.matcher(t);
        if (date.find() && date.start() > 0) {
            boolean extended = date.group().contains("연장");
            t = t.substring(0, date.start());
            if (extended) {
                t = t + " (기간 연장)";
            }
        }
        return t.replaceAll("\\s+", " ").trim();
    }
}
