package com.cukalimi.notice;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** 공지 목록 페이지를 읽는다. 상단 고정 공지는 본문 목록에도 다시 나오므로 글 번호로 한 번만 남긴다. */
public final class NoticeListParser {

    private static final Pattern DATE = Pattern.compile("(20\\d{2})\\.(\\d{2})\\.(\\d{2})");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private NoticeListParser() {
    }

    public static List<NoticeSummary> parse(String html) {
        Document doc = Jsoup.parse(html);
        Set<Long> seen = new LinkedHashSet<>();
        List<NoticeSummary> result = new ArrayList<>();
        for (Element row : doc.select("tbody > tr")) {
            Element link = row.selectFirst("a.b-title[data-article-no]");
            if (link == null) {
                continue;
            }
            long articleNo = Long.parseLong(link.attr("data-article-no"));
            if (!seen.add(articleNo)) {
                continue;
            }
            String category = row.select("td").size() > 1 ? row.select("td").get(1).text().trim() : "";
            // 제목에도 "<2026.09.04. 업데이트>"처럼 날짜가 들어가므로, 등록일은 제목 뒤쪽 칸에서 찾는다.
            LocalDate postedOn = null;
            List<Element> tds = row.select("td");
            for (int i = tds.size() - 1; i >= 0; i--) {
                Matcher m = DATE.matcher(tds.get(i).text());
                if (m.find() && !tds.get(i).hasClass("b-td-title")) {
                    postedOn = LocalDate.parse(m.group(), FORMAT);
                    break;
                }
            }
            if (postedOn == null) {
                continue;
            }
            result.add(new NoticeSummary(articleNo, category, link.text().trim(), postedOn));
        }
        return result;
    }
}
