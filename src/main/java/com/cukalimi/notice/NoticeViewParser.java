package com.cukalimi.notice;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** 공지 본문 페이지를 읽는다. 제목 .b-title, 분류 .b-cate, 등록일 .b-etc-box, 본문 .b-con-box .fr-view, 첨부 .b-file-box. */
public final class NoticeViewParser {

    private static final Pattern DATE = Pattern.compile("(20\\d{2})\\.(\\d{2})\\.(\\d{2})");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private NoticeViewParser() {
    }

    public static NoticeView parse(String html) {
        Document doc = Jsoup.parse(html);
        Element main = doc.selectFirst(".b-main-box");
        if (main == null) {
            throw new IllegalStateException("공지 본문(.b-main-box)을 찾지 못했습니다");
        }
        long articleNo = Long.parseLong(main.selectFirst("input[name=articleNo]").attr("value"));
        // 학교 페이지는 <p class="b-title"><p class="b-title">제목</p></p> 로 겹쳐 있어 첫 번째는 비어 있다.
        String title = main.select(".b-title").eachText().stream()
                .map(String::trim).filter(s -> !s.isEmpty()).reduce((a, b) -> b).orElse("");
        String category = text(main.selectFirst(".b-cate"));
        String writer = "";
        LocalDate postedOn = null;
        for (Element li : main.select(".b-etc-box li")) {
            String label = text(li.selectFirst(".title"));
            if (label.startsWith("작성자")) {
                writer = li.select("span").last().text().trim();
            } else if (label.startsWith("등록일")) {
                Matcher m = DATE.matcher(li.text());
                if (m.find()) {
                    postedOn = LocalDate.parse(m.group(), FORMAT);
                }
            }
        }
        if (postedOn == null) {
            throw new IllegalStateException("등록일을 찾지 못했습니다: " + articleNo);
        }
        Element body = main.selectFirst(".b-con-box .fr-view");
        if (body == null) {
            body = main.selectFirst(".b-con-box");
        }
        if (body == null) {
            body = new Element("div");
        }
        String bodyText = body.text().trim();
        boolean hasImages = !body.select("img").isEmpty();
        List<String> attachments = main.select(".b-file-box a").eachText().stream()
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new NoticeView(articleNo, category, title, postedOn, writer, body, bodyText, hasImages, attachments);
    }

    private static String text(Element e) {
        return e == null ? "" : e.text().trim();
    }
}
