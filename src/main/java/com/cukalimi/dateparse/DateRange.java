package com.cukalimi.dateparse;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 글자에서 읽은 날짜 하나 또는 기간 하나.
 * start가 null이면 "~ 10. 30.까지"처럼 끝만 적힌 경우다.
 */
public record DateRange(LocalDate start, LocalDate end, LocalTime startsAt, LocalTime endsAt, String matchedText) {

    public boolean isSingleDay() {
        return start != null && start.equals(end);
    }
}
