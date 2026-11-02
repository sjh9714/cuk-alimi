package com.cukalimi.schedule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 학사일정 표 페이지를 읽는다. 학교가 날짜를 구조로 주므로 추측할 것이 없다.
 *
 * <p>구조: .b-cal-list-box 아래 월 블록(p "3월") 하나에 항목 div 여러 개. 항목은 p에 "4 (화) ~ 7 (금)" 또는
 * "31 (월) ~ 09.04(금)", li에 제목, .b-cal-list-cate 아래 span에 교정 이름.
 * 학년도 페이지는 3월부터 다음 해 2월까지라 1월·2월은 다음 해로 본다.
 */
public final class ScheduleParser {

    private static final Pattern MONTH = Pattern.compile("(\\d{1,2})\\s*월");
    private static final Pattern DAY = Pattern.compile("^\\s*(\\d{1,2})");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})\\s*\\.\\s*(\\d{1,2})");

    private ScheduleParser() {
    }

    public static List<ScheduleEntry> parse(String html, int academicYear) {
        Document doc = Jsoup.parse(html);
        Element box = doc.selectFirst(".b-cal-list-box");
        if (box == null) {
            throw new IllegalStateException("학사일정 목록(.b-cal-list-box)을 찾지 못했습니다");
        }
        List<ScheduleEntry> entries = new ArrayList<>();
        for (Element monthBlock : box.children()) {
            Element monthLabel = monthBlock.selectFirst("> p");
            if (monthLabel == null) {
                continue;
            }
            Matcher mm = MONTH.matcher(monthLabel.text());
            if (!mm.find()) {
                continue;
            }
            int month = Integer.parseInt(mm.group(1));
            int year = month <= 2 ? academicYear + 1 : academicYear;
            for (Element item : monthBlock.select("> div > div")) {
                Element dayText = item.selectFirst("> p");
                Element title = item.selectFirst("ul > li");
                if (dayText == null || title == null) {
                    continue;
                }
                ScheduleEntry entry = toEntry(dayText.text(), title, year, month);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    private static ScheduleEntry toEntry(String dayText, Element titleElement, int year, int month) {
        String[] parts = dayText.split("[~∼〜～]");
        Matcher startMatcher = DAY.matcher(parts[0]);
        if (!startMatcher.find()) {
            return null;
        }
        LocalDate start = LocalDate.of(year, month, Integer.parseInt(startMatcher.group(1)));
        LocalDate end = start;
        if (parts.length > 1) {
            Matcher md = MONTH_DAY.matcher(parts[1]);
            Matcher d = DAY.matcher(parts[1]);
            if (md.find()) {
                int endMonth = Integer.parseInt(md.group(1));
                int endYear = endMonth < month ? year + 1 : year;
                end = LocalDate.of(endYear, endMonth, Integer.parseInt(md.group(2)));
            } else if (d.find()) {
                end = LocalDate.of(year, month, Integer.parseInt(d.group(1)));
                if (end.isBefore(start)) {
                    end = end.plusMonths(1);
                }
            }
        }
        List<String> campuses = titleElement.select(".b-cal-list-cate span").eachText();
        String title = titleElement.ownText().trim();
        if (title.isEmpty()) {
            return null;
        }
        return new ScheduleEntry(title, start, end, campuses);
    }
}
