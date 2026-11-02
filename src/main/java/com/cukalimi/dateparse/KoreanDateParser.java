package com.cukalimi.dateparse;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 학교 공지에 흔한 한국어 날짜 표기를 읽는다.
 *
 * <p>읽는 것: "2026. 7. 7.(화) ~ 7. 12.(일) / 09:00 ~ 23:59", "10. 14.(수) ~ 11. 13.(금) 18:00까지",
 * "(~10.26)", "(10/21~10/29)", "2026년 10월 30일", "2026-10-30", "10. 30.까지", "`26.10.19", "26.10.26 (월)".
 *
 * <p>읽지 않는 것: 연도·요일·"일"이 없는 월.일 표기가 혼자 있는 것("수업일수 1/4", "3.0 이상"),
 * 점으로 쓴 소수 쌍("평점 2.5 ~ 3.5"), 몇째 주("11. 1주"), 뒤에 "이상·점·학점·%"가 붙는 것, 끝이 시작보다 앞선 범위,
 * "10.26(월) 09:00부터"처럼 시작만 있는 날짜, 표시도 요일도 없는 두 자리 연도("v26.10.19").
 * 추측하느니 비워 두고 검수로 보내는 편이 낫다. 연도가 없으면 게시일 기준으로 가장 가까운 앞날을 고른다.
 *
 * <p>시각 규칙. 날짜 하나에 시각이 하나면 마감 시각(endsAt)이다. 둘이면 시작·끝이다.
 */
public final class KoreanDateParser {

    private static final String FULL = "(?<y>(?:19|20)\\d{2})\\s*[.\\-/년]\\s*(?<m>\\d{1,2})\\s*[.\\-/월]\\s*(?<d>\\d{1,2})\\s*\\.?\\s*일?";
    /** 두 자리 연도. "`26.10.19"처럼 앞에 표시가 있거나 "26.10.26 (월)"처럼 요일이 붙을 때만 믿는다. */
    private static final String SHORT = "(?:[`'’‘]\\s*(?<ym>\\d{2})|(?<yw>\\d{2})(?=\\s*\\.\\s*\\d{1,2}\\s*\\.\\s*\\d{1,2}\\s*\\.?\\s*\\(\\s*[월화수목금토일]\\s*\\)))"
            + "\\s*\\.\\s*(?<sm>\\d{1,2})\\s*\\.\\s*(?<sd>\\d{1,2})\\s*\\.?";
    private static final String MD = "(?<mm>\\d{1,2})\\s*(?<sep>[./월])\\s*(?<dd>\\d{1,2})\\s*\\.?\\s*(?<daySuffix>일)?";
    private static final String WEEKDAY = "(?<weekday>\\(\\s*[월화수목금토일]\\s*\\))?";
    private static final String TIME = "(?:\\s*/?\\s*(?<h1>\\d{1,2}):(?<n1>\\d{2})(?:\\s*[~∼〜～\\-–]\\s*(?<h2>\\d{1,2}):(?<n2>\\d{2}))?)?";
    private static final String UNTIL = "(?<until>\\s*까지)?";

    private static final Pattern TOKEN = Pattern.compile(
            "(?<![\\d:])(?:" + FULL + "|" + SHORT + "|" + MD + ")\\s*" + WEEKDAY + TIME + UNTIL);
    private static final Pattern CONNECTOR = Pattern.compile("^\\s*(?:[~∼〜～\\-–—]|부터|부터\\s*[~∼〜～\\-–—])\\s*$");
    /**
     * 뒤에 끝 날짜 없이 "부터"나 물결만 오는 날짜는 시작일이다. 마감으로 쓰지 않는다.
     * "10.26(월) 09:00부터 선착순", "2026.10.26.(월) 오전 9시 ~ (선착순 마감)".
     */
    private static final Pattern START_ONLY = Pattern.compile(
            "^\\s*(?:부터|(?:오전|오후)?\\s*(?:\\d{1,2}\\s*시)?\\s*[~∼〜～]\\s*(?:$|[(（]|[^\\s\\d`'’‘]))");
    private static final Pattern LEADING_TILDE = Pattern.compile("[~∼〜～]\\s*$");
    /** 날짜가 아니라 수치를 뜻하는 뒷말. "3.0 이상", "4.5점", "2.5 학점", "12.5%" */
    private static final Pattern NUMERIC_TAIL = Pattern.compile("^\\s*(?:이상|이하|초과|미만|점|학점|%|배|kg|km|원)");

    private final LocalDate baseDate;

    /** @param baseDate 연도가 적히지 않은 날짜를 어느 해로 볼지 정하는 기준. 보통 공지 게시일. */
    public KoreanDateParser(LocalDate baseDate) {
        this.baseDate = baseDate;
    }

    public List<DateRange> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Token> tokens = tokenize(text);
        List<DateRange> ranges = new ArrayList<>();
        int i = 0;
        while (i < tokens.size()) {
            Token t = tokens.get(i);
            if (i + 1 < tokens.size()) {
                Token next = tokens.get(i + 1);
                String between = text.substring(t.end, next.start);
                if (CONNECTOR.matcher(between).matches()) {
                    DateRange range = buildRange(t, next, text);
                    if (range != null) {
                        ranges.add(range);
                    }
                    i += 2;
                    continue;
                }
            }
            if (START_ONLY.matcher(text.substring(t.end)).find()) {
                i++;
                continue;
            }
            String before = text.substring(Math.max(0, t.start - 4), t.start);
            boolean endOnly = LEADING_TILDE.matcher(before).find();
            DateRange single = buildSingle(t, endOnly, text);
            if (single != null) {
                ranges.add(single);
            }
            i++;
        }
        return ranges;
    }

    private DateRange buildRange(Token a, Token b, String text) {
        // "평점 2.5 ~ 3.5"처럼 점으로 쓴 월.일 쌍은 둘 다 요일·연도·"일"이 없으면 소수로 본다. 슬래시(10/21~10/29)는 소수가 아니다.
        if (!a.selfSufficient() && !b.selfSufficient() && (a.dotSeparated || b.dotSeparated)) {
            return null;
        }
        LocalDate start;
        LocalDate end;
        if (a.year != null) {
            start = resolve(a, a.year);
            end = b.year != null ? resolve(b, b.year) : resolve(b, start == null ? null : start.getYear());
            if (start != null && end != null && end.isBefore(start) && b.year == null) {
                end = end.plusYears(1);
            }
        } else if (b.year != null) {
            // "12. 20.(일) ~ 2027. 1. 5.(화)" 처럼 연도가 뒤에만 있으면 시작은 끝과 같은 해, 끝보다 늦으면 한 해 전
            end = resolve(b, b.year);
            start = end == null ? null : resolve(a, end.getYear());
            if (start != null && end != null && start.isAfter(end)) {
                start = start.minusYears(1);
            }
        } else {
            start = resolve(a, null);
            end = start == null ? null : resolve(b, start.getYear());
            if (start != null && end != null && end.isBefore(start)) {
                end = end.plusYears(1);
            }
        }
        if (start == null || end == null) {
            return null;
        }
        // 한 해를 넘기는 기간은 거의 없다. 1년 가까이 벌어지면 거꾸로 적힌 것이니 버린다.
        if (end.isAfter(start.plusDays(300))) {
            return null;
        }
        LocalTime startsAt;
        LocalTime endsAt;
        if (a.time1 != null) {
            startsAt = a.time1;
            endsAt = b.time2 != null ? b.time2 : (b.time1 != null ? b.time1 : a.time2);
        } else if (b.time2 != null) {
            startsAt = b.time1;
            endsAt = b.time2;
        } else {
            startsAt = null;
            endsAt = b.time1;
        }
        return new DateRange(start, end, startsAt, endsAt, text.substring(a.start, b.end).trim());
    }

    private DateRange buildSingle(Token t, boolean endOnly, String text) {
        // "(~10.26)" 처럼 물결이 앞에 붙은 월.일은 마감일로 적은 것이라 요일이 없어도 믿는다.
        if (!t.selfSufficient() && !endOnly) {
            return null;
        }
        LocalDate day = resolve(t, t.year);
        if (day == null) {
            return null;
        }
        String matched = text.substring(t.start, t.end).trim();
        if (endOnly) {
            return new DateRange(null, day, null, t.time1, "~" + matched);
        }
        if (t.time2 != null) {
            return new DateRange(day, day, t.time1, t.time2, matched);
        }
        return new DateRange(day, day, null, t.time1, matched);
    }

    private LocalDate resolve(Token t, Integer explicitYear) {
        try {
            if (explicitYear != null) {
                return LocalDate.of(explicitYear, t.month, t.day);
            }
            LocalDate candidate = LocalDate.of(baseDate.getYear(), t.month, t.day);
            if (candidate.isBefore(baseDate.minusDays(60))) {
                candidate = candidate.plusYears(1);
            }
            return candidate;
        } catch (DateTimeException e) {
            return null;
        }
    }

    private List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            Token t = new Token();
            t.start = m.start();
            t.end = m.end();
            String dayGroup;
            if (m.group("y") != null) {
                t.year = Integer.parseInt(m.group("y"));
                t.month = Integer.parseInt(m.group("m"));
                t.day = Integer.parseInt(m.group("d"));
                dayGroup = "d";
            } else if (m.group("sm") != null) {
                t.year = 2000 + Integer.parseInt(m.group("ym") != null ? m.group("ym") : m.group("yw"));
                t.month = Integer.parseInt(m.group("sm"));
                t.day = Integer.parseInt(m.group("sd"));
                dayGroup = "sd";
            } else {
                t.month = Integer.parseInt(m.group("mm"));
                t.dotSeparated = ".".equals(m.group("sep"));
                t.day = Integer.parseInt(m.group("dd"));
                t.hasDaySuffix = m.group("daySuffix") != null;
                dayGroup = "dd";
            }
            t.hasWeekday = m.group("weekday") != null;
            t.time1 = time(m.group("h1"), m.group("n1"));
            t.time2 = time(m.group("h2"), m.group("n2"));
            t.hasUntil = m.group("until") != null;
            if (t.month < 1 || t.month > 12 || t.day < 1 || t.day > 31) {
                continue;
            }
            // "2026. 11. 1주 예정"의 1은 날이 아니라 11월 첫째 주다.
            if (text.startsWith("주", m.end(dayGroup))) {
                continue;
            }
            // 연도·요일·"까지"가 붙은 날짜는 뒤에 무엇이 와도 날짜다. "까지 학점교류"의 "학점"은 수치가 아니다.
            if (t.year == null && !t.hasWeekday && !t.hasUntil && NUMERIC_TAIL.matcher(text.substring(t.end)).find()) {
                continue;
            }
            tokens.add(t);
        }
        return tokens;
    }

    private static LocalTime time(String h, String min) {
        if (h == null) {
            return null;
        }
        try {
            return LocalTime.of(Integer.parseInt(h), Integer.parseInt(min));
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static final class Token {
        int start;
        int end;
        Integer year;
        int month;
        int day;
        boolean dotSeparated;
        boolean hasWeekday;
        boolean hasDaySuffix;
        boolean hasUntil;
        LocalTime time1;
        LocalTime time2;

        /** 혼자서도 날짜로 믿을 만한가. 연도가 있거나, 요일이 붙었거나, "일"이나 "까지"로 끝나면 그렇다. */
        boolean selfSufficient() {
            return year != null || hasWeekday || hasDaySuffix || hasUntil;
        }
    }
}
