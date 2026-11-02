package com.cukalimi.notice;

import java.time.LocalDate;

/** 공지 목록의 한 줄. */
public record NoticeSummary(long articleNo, String category, String title, LocalDate postedOn) {
}
