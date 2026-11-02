package com.cukalimi.notice;

import java.time.LocalDate;
import java.util.List;
import org.jsoup.nodes.Element;

/** 공지 본문 페이지에서 읽은 것. body는 .fr-view 요소라 표와 문단을 그대로 다시 볼 수 있다. */
public record NoticeView(
        long articleNo,
        String category,
        String title,
        LocalDate postedOn,
        String writer,
        Element body,
        String bodyText,
        boolean hasImages,
        List<String> attachments) {

    /** "[학사지원팀] 2026학년도 ... 안내(10. 14.(수) ~ 11. 13.(금))" 에서 부서와 날짜 괄호를 뗀 제목. */
    public String cleanTitle() {
        String t = title.replaceFirst("^\\s*\\[[^\\]]*\\]\\s*", "").replace("﻿", "").trim();
        // 끝에 붙은 괄호 묶음이 날짜를 담고 있으면 뗀다. "(10. 14.(수) ~ 11. 13.(금))"처럼 괄호가 겹쳐도 된다.
        if (t.endsWith(")")) {
            int depth = 0;
            for (int i = t.length() - 1; i >= 0; i--) {
                char c = t.charAt(i);
                if (c == ')') {
                    depth++;
                } else if (c == '(') {
                    depth--;
                    if (depth == 0) {
                        String tail = t.substring(i);
                        if (tail.matches("(?s).*\\d{1,2}\\s*[./월]\\s*\\d{1,2}.*")) {
                            t = t.substring(0, i);
                        }
                        break;
                    }
                }
            }
        }
        return t.trim();
    }
}
