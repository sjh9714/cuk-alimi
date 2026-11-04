package com.cukalimi.notice;

import java.time.LocalDate;

/** 공지 목록의 한 줄. url과 dept는 학과 게시판 글에만 있고, 중앙 게시판 글은 null이라 설정의 주소를 쓴다. */
public record NoticeSummary(long articleNo, String category, String title, LocalDate postedOn, String url, String dept) {

    public NoticeSummary(long articleNo, String category, String title, LocalDate postedOn) {
        this(articleNo, category, title, postedOn, null, null);
    }
}
