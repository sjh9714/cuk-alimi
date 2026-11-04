package com.cukalimi.feed;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 홈 피드를 iCalendar(RFC 5545) 글로 바꾼다. 구글·애플 캘린더가 구독 주소로 읽는다 (ADR 004).
 * 넣는 것은 내게 맞는 일과 저장한 일이다. 시각이 없는 마감은 그 날 하루 일정, 시각이 있으면 그 시각으로 끝나는 일정이다.
 */
public final class Ics {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private Ics() {
    }

    /** 카드가 내게 맞으면 그 카드의 날짜 줄 모두, 아니면 저장한 줄만 넣는다. */
    public static String of(Feed.View view, LocalDateTime stamp) {
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//cuk-alimi//KO");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:PUBLISH");
        lines.add("X-WR-CALNAME:CUK 알리미");
        lines.add("X-WR-TIMEZONE:Asia/Seoul");
        String dtstamp = UTC.format(stamp.atZone(SEOUL).withZoneSameInstant(ZoneOffset.UTC));
        for (Feed.Section section : view.sections()) {
            for (Feed.Card card : section.cards()) {
                if (card.deadline() != null && (card.mine() || card.deadline().saved())) {
                    event(lines, card, card.deadline(), dtstamp);
                }
                for (Feed.Line next : card.next()) {
                    if (card.mine() || next.saved()) {
                        event(lines, card, next, dtstamp);
                    }
                }
            }
        }
        lines.add("END:VCALENDAR");
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(fold(line)).append("\r\n");
        }
        return out.toString();
    }

    private static void event(List<String> out, Feed.Card card, Feed.Line line, String dtstamp) {
        out.add("BEGIN:VEVENT");
        out.add("UID:item-" + line.id() + "@cuk-alimi");
        out.add("DTSTAMP:" + dtstamp);
        String title = line.label() == null || line.label().isBlank() ? card.title() : card.title() + " · " + line.label();
        out.add("SUMMARY:" + escape(title));
        if (line.endsAt() == null) {
            LocalDate start = line.startsOn() == null || line.startsOn().isAfter(line.endsOn()) ? line.endsOn() : line.startsOn();
            out.add("DTSTART;VALUE=DATE:" + DATE.format(start));
            out.add("DTEND;VALUE=DATE:" + DATE.format(line.endsOn().plusDays(1)));
        } else {
            LocalTime startAt = line.startsAt() == null ? line.endsAt().minusHours(1) : line.startsAt();
            LocalDate startOn = line.startsOn() == null ? line.endsOn() : line.startsOn();
            LocalDateTime start = LocalDateTime.of(startOn, startAt);
            LocalDateTime end = LocalDateTime.of(line.endsOn(), line.endsAt());
            if (!start.isBefore(end)) {
                start = end.minusHours(1);
            }
            out.add("DTSTART:" + UTC.format(start.atZone(SEOUL).withZoneSameInstant(ZoneOffset.UTC)));
            out.add("DTEND:" + UTC.format(end.atZone(SEOUL).withZoneSameInstant(ZoneOffset.UTC)));
        }
        String why = card.mineReason() == null ? "" : card.mineReason() + "\n";
        out.add("DESCRIPTION:" + escape(why + card.sourceTitle() + "\n" + card.url()));
        out.add("URL:" + card.url());
        out.add("END:VEVENT");
    }

    /** RFC 5545 3.3.11. 역슬래시, 세미콜론, 쉼표, 줄바꿈을 피한다. */
    static String escape(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\n").replace("\n", "\\n");
    }

    /** RFC 5545 3.1. 한 줄은 75옥텟까지다. 한글은 3바이트라 글자 경계에서 자른다. */
    static String fold(String line) {
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 75) {
            return line;
        }
        StringBuilder out = new StringBuilder();
        int limit = 75;
        int count = 0;
        for (int i = 0; i < line.length();) {
            int cp = line.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int size = ch.getBytes(StandardCharsets.UTF_8).length;
            if (count + size > limit) {
                out.append("\r\n ");
                count = 1;
                limit = 75;
            }
            out.append(ch);
            count += size;
            i += Character.charCount(cp);
        }
        return out.toString();
    }
}
