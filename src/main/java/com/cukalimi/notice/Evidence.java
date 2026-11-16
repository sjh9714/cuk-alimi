package com.cukalimi.notice;

import com.cukalimi.dateparse.DateRange;
import java.util.List;

/**
 * 날짜를 읽은 자리 (ADR 016). text는 공지에서 그 날짜가 적힌 줄, 표의 행, 또는 제목이고 match는 그 안의 날짜 글자다.
 * 화면은 text를 그대로 보여 주고 match에 형광펜을 긋는다. 둘 다 공지의 글자를 옮긴 것이고 고쳐 쓰지 않는다.
 * match가 text 안에 없으면 match는 null이다. 그때는 형광펜 없이 글만 보인다.
 */
public record Evidence(String text, String match) {

    /** 화면에 펼칠 근거의 길이 상한. 긴 문단은 날짜 둘레만 남긴다. */
    static final int LIMIT = 240;

    /**
     * @param source 날짜를 읽은 글. 줄, 표의 행(칸을 " | "로 이은 것), 제목.
     * @param found  파서가 그 글에서 읽은 날짜들
     * @param chosen 항목이 된 기간. 여러 날짜를 합친 것이면 마감(끝 날짜)을 적은 글자에 형광펜을 긋는다.
     */
    public static Evidence of(String source, List<DateRange> found, DateRange chosen) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String text = source.strip();
        String match = null;
        // 합친 기간은 첫 날짜의 글자만 들고 있다. 마감은 끝 날짜이므로 끝이 같은 날짜 가운데 마지막 것을 고른다.
        for (DateRange r : found) {
            if (r.end().equals(chosen.end())) {
                match = r.matchedText();
            }
        }
        if (match == null) {
            match = chosen.matchedText();
        }
        // 끝만 적힌 날짜("~ 10. 30.")는 파서가 앞에 "~"를 붙여 둔다. 원문 글자가 아니다.
        match = match == null ? "" : match.replaceFirst("^~", "").strip();
        int at = match.isEmpty() ? -1 : text.indexOf(match);
        if (at < 0) {
            return new Evidence(clip(text, 0, 0), null);
        }
        return new Evidence(clip(text, at, match.length()), match);
    }

    /** 날짜 둘레를 남기고 자른다. 잘린 쪽에는 "…"를 붙인다. 날짜 글자는 잘리지 않는다. */
    private static String clip(String text, int at, int length) {
        if (text.length() <= LIMIT) {
            return text;
        }
        int room = Math.max(0, LIMIT - length);
        int from = Math.max(0, at - room / 2);
        int to = Math.min(text.length(), from + Math.max(LIMIT, length));
        from = Math.max(0, Math.min(from, to - Math.max(LIMIT, length)));
        return (from > 0 ? "…" : "") + text.substring(from, to).strip() + (to < text.length() ? "…" : "");
    }
}
