package com.cukalimi.schedule;

import java.time.LocalDate;
import java.util.List;

/** 학사일정 표의 한 줄. */
public record ScheduleEntry(String title, LocalDate startsOn, LocalDate endsOn, List<String> campuses) {

    /**
     * 같은 학년도 안에서 같은 제목이 몇 번째인지로 키를 만든다. 날짜를 키에 넣지 않아야
     * 학교가 날짜를 고쳤을 때 옛 항목이 남지 않고 갱신된다.
     */
    public String naturalKey(int academicYear, int ordinal) {
        return "schedule:" + academicYear + ":" + title + "#" + ordinal;
    }
}
