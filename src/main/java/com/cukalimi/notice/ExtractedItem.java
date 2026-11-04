package com.cukalimi.notice;

import com.cukalimi.dateparse.DateRange;

/**
 * 공지 한 건에서 뽑은 날짜 항목 하나. targetGrade는 "ALL", "1"~"4", 또는 모르면 null.
 * where는 항목이 나온 자리(title, table1.r2.c2, line7)이고 진단용이다.
 * 항목의 키는 자리가 아니라 이름이다(2026-11-04). 자리로 키를 만들면 학교가 표에 행을 하나 끼워 넣을 때
 * 학생이 저장한 마감이 다른 마감으로 바뀐다. 같은 이름이 여럿이면 나온 순서(occurrence)로 가른다.
 */
public record ExtractedItem(String title, DateRange range, String targetGrade, String where) {

    public String naturalKey(long articleNo, int occurrence) {
        String name = NoticeCollector.normalize(title);
        if (name.length() > 60) {
            name = name.substring(0, 60);
        }
        return "notice:" + articleNo + ":t:" + name + "#" + occurrence;
    }
}
