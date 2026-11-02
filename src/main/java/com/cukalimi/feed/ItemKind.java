package com.cukalimi.feed;

import java.util.regex.Pattern;

/**
 * 날짜 항목의 성격. ACTION은 학생이 그날까지 무언가를 해야 하는 날(신청·제출·납부·변경),
 * INFO는 지켜보기만 하는 날(발표·면접·수업기간·수여식)이다. 마감 목록에는 ACTION만 줄 세운다.
 * 모르면 ACTION으로 둔다. 할 일을 안내로 잘못 넣어 놓치는 쪽이 더 위험하다.
 */
public final class ItemKind {

    public static final String ACTION = "ACTION";
    public static final String INFO = "INFO";

    private static final Pattern INFO_TITLE = Pattern.compile(
            "발표|결과|면접|수업\\s*기간|근로\\s*기간|수여식|전달식|시상식|개시일|입학미사|개강|종강|설명회|오리엔테이션"
                    + "|^(대학|재단|최종)\\s*선발$|공고$|^계절학기\\s*수업|^수업|수업$"
                    + "|지급(\\s*(예정|일|일정))?$|^((행사|특강|강연|교육)\\s*)?일\\s*시$"
                    + "|(운영|근무|진행)\\s*기간|특강(?!\\s*신청)|(^|[^A-Za-z])OT([^A-Za-z]|$)|조회$");

    /** 날짜만 알아 두면 되는 행사. 특강과 면접은 넣지 않는다. 대상이 좁거나 수가 많아 판을 덮는다. */
    private static final Pattern EVENT = Pattern.compile(
            "수여식|전달식|시상식|입학미사|개교기념|축제|개강|종강|설명회|오리엔테이션|(^|[^A-Za-z])OT([^A-Za-z]|$)");
    /** 표의 칸 이름이 "일시"뿐이면 무슨 행사인지는 공지 제목에 있다. */
    private static final Pattern WHEN_ONLY = Pattern.compile("^(행사\\s*)?일\\s*시$");

    private ItemKind() {
    }

    /** 행사 날짜인가. 수업기간처럼 긴 일정이 판을 덮지 않게 하루짜리만 본다. */
    public static boolean isEvent(String itemTitle, String noticeTitle, boolean singleDay) {
        if (!singleDay || !INFO.equals(of(itemTitle))) {
            return false;
        }
        return EVENT.matcher(itemTitle).find()
                || (noticeTitle != null && WHEN_ONLY.matcher(itemTitle).find() && EVENT.matcher(noticeTitle).find());
    }

    public static String of(String itemTitle) {
        return itemTitle != null && INFO_TITLE.matcher(itemTitle).find() ? INFO : ACTION;
    }
}
