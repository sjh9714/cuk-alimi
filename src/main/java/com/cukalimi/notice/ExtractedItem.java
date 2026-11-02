package com.cukalimi.notice;

import com.cukalimi.dateparse.DateRange;

/**
 * 공지 한 건에서 뽑은 날짜 항목 하나. targetGrade는 "ALL", "1"~"4", 또는 모르면 null.
 * where는 항목이 나온 자리(title, table1.r2.c2, line7)다. 파서 규칙을 고쳐 다시 돌려도 같은 자리면 같은 항목이다.
 */
public record ExtractedItem(String title, DateRange range, String targetGrade, String where) {

    public String naturalKey(long articleNo) {
        return "notice:" + articleNo + ":" + where;
    }
}
