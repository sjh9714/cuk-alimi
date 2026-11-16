package com.cukalimi.notice;

import com.cukalimi.dateparse.DateRange;
import com.cukalimi.dateparse.KoreanDateParser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AI가 옮겨 온 인용문을 원문에 맞춰 보고, 맞으면 원문의 그 줄에서 날짜를 읽는다 (ADR 016).
 *
 * <p>지키는 것은 하나다. 날짜 파서에는 공지의 글자만 들어간다. AI가 적은 글자에서는 날짜를 읽지 않는다.
 * 인용문은 "공지의 어느 줄인가"를 찾는 데만 쓰고, 날짜는 그 줄 전체를 규칙 추출과 같은 파서에 넣어 읽는다.
 * 인용문이 원문에 글자 그대로 없으면 버린다. AI가 날짜를 지어내도 화면에 나갈 길이 없다.
 */
public final class QuoteCheck {

    /** 이보다 짧은 인용은 아무 줄에나 들어맞는다. 공백을 뺀 글자 수다. */
    static final int MIN_QUOTE = 6;
    /** 인용 하나가 걸칠 수 있는 줄 수. 그보다 길면 한 줄을 옮기라는 지시를 어긴 것이다. */
    static final int MAX_LINES = 3;

    public enum Status {
        /** 원문에 있고 날짜도 읽었다. */
        READ,
        /** 원문에 글자 그대로 없다. 지어냈거나 고쳐 썼다. */
        UNVERIFIED,
        /** 원문에는 있지만 파서가 그 자리에서 날짜를 읽지 못했다. 숫자가 없는 줄도 여기다. 아무 일도 일어나지 않는다. */
        NO_DATE
    }

    /**
     * @param source 인용이 들어 있던 원문의 줄(여러 줄이면 이어 붙임). 근거로 쓴다
     * @param ranges 그 줄에서 파서가 읽은 날짜 가운데 인용 안에 든 것
     */
    public record Located(Status status, LocatorReading.Found found, String source, List<DateRange> ranges) {
        public boolean read() {
            return status == Status.READ;
        }
    }

    private QuoteCheck() {
    }

    public static List<Located> check(NoticeText text, LocalDate postedOn, LocatorReading reading) {
        List<Located> out = new ArrayList<>();
        for (LocatorReading.Found f : reading.found()) {
            out.add(check(text, postedOn, f));
        }
        return out;
    }

    static Located check(NoticeText text, LocalDate postedOn, LocatorReading.Found found) {
        String quote = normalize(found.quote());
        if (quote.length() < MIN_QUOTE) {
            return new Located(Status.UNVERIFIED, found, null, List.of());
        }
        boolean digits = quote.chars().anyMatch(Character::isDigit);
        List<String> lines = text.lines();
        // 한 줄에서 먼저 찾는다. 없으면 이어진 두 줄, 세 줄에 걸쳤는지 본다.
        for (int width = 1; width <= MAX_LINES; width++) {
            for (int i = 0; i + width <= lines.size(); i++) {
                List<String> window = lines.subList(i, i + width);
                StringBuilder joined = new StringBuilder();
                window.forEach(l -> joined.append(normalize(l)));
                if (joined.indexOf(quote) < 0) {
                    continue;
                }
                // 숫자가 없는 줄은 원문에 있어도 날짜가 없다. 지어낸 것(UNVERIFIED)과는 가른다.
                List<DateRange> ranges = digits ? datesInside(window, quote, postedOn) : List.of();
                String source = String.join(" ", window);
                return new Located(ranges.isEmpty() ? Status.NO_DATE : Status.READ, found, source, ranges);
            }
        }
        return new Located(Status.UNVERIFIED, found, null, List.of());
    }

    /**
     * 원문의 줄을 통째로 파서에 넣는다. 잘린 인용문만 넣으면 해가 빠진 날짜의 연도 추정이 달라질 수 있다.
     * 읽은 날짜 가운데 인용문 안에 그 글자가 들어 있는 것만 남긴다. 같은 줄의 다른 날짜(발표일 등)는 AI가 가리킨 것이 아니다.
     * 게시일보다 앞서거나 한 해 넘게 뒤인 날짜는 마감이 아니다(출생연도, 개정일).
     */
    private static List<DateRange> datesInside(List<String> window, String quote, LocalDate postedOn) {
        KoreanDateParser parser = new KoreanDateParser(postedOn);
        List<DateRange> kept = new ArrayList<>();
        for (String line : window) {
            for (DateRange r : parser.parse(line)) {
                String matched = normalize(r.matchedText().replaceFirst("^~", ""));
                boolean inWindow = !r.end().isBefore(postedOn.minusDays(1)) && !r.end().isAfter(postedOn.plusYears(1));
                if (!matched.isEmpty() && quote.contains(matched) && inWindow) {
                    kept.add(r);
                }
            }
        }
        return kept;
    }

    /** 맞춰 볼 때는 공백, 보이지 않는 글자, 표의 칸 구분(" | ")을 뺀다. 글자 자체는 바꾸지 않는다. */
    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[\\s\\u00a0\\u200b\\u200c\\u200d\\ufeff|]", "");
    }
}
