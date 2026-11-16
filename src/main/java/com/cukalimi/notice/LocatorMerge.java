package com.cukalimi.notice;

import com.cukalimi.dateparse.DateRange;
import com.cukalimi.feed.ItemKind;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 규칙이 뽑은 날짜와 AI가 찾은 문장을 합친다 (ADR 016). 운영 코드와 백테스트가 같은 함수를 쓴다.
 *
 * <p>단위는 공지 안의 마감일이다. 작은 모델이 같은 공지를 두 번 읽는다. 그 답이 화면을 바꾸려 할 때만(규칙에 없는 마감을 더하거나
 * 규칙의 마감을 의심할 때) 큰 모델이 같은 지시문으로 한 번 읽어 확인한다. 큰 모델이 같은 말을 해야 화면이 바뀐다.
 *
 * <table>
 * <tr><th>규칙</th><th>작은 모델</th><th>큰 모델</th><th>결과</th></tr>
 * <tr><td>할 일로 찾음</td><td>할 일 또는 말 없음</td><td>부르지 않음</td><td>그대로 보인다</td></tr>
 * <tr><td>할 일로 찾음</td><td>할 일이 아니라 함</td><td>할 일이 아니라 함</td><td>의심(doubt). 숨기지 않고 "확인"을 달아 보인다</td></tr>
 * <tr><td>못 찾음</td><td>할 일이라 함</td><td>할 일이라 함</td><td>더한다(added). "확인"을 달아 보인다</td></tr>
 * <tr><td>못 찾음</td><td>할 일이라 함</td><td>다른 말, 또는 부르지 못함</td><td>보류(held). 검수를 기다린다</td></tr>
 * </table>
 *
 * 처음에는 작은 모델 두 표가 모이면 공개하려 했다. 2026-11-16 개발셋에서 재 보니 두 읽기는 틀릴 때 같이 틀렸다. 공지 122건을 두 번씩
 * 읽는 동안 결과를 바꿀 만큼 엇갈린 적이 없었고, 잘못 더한 날짜 셋은 모두 두 표였다. 같은 모델의 두 표는 확인이 되지 못한다.
 *
 * <p>놓치는 것이 잘못 보여 주는 것보다 아프다. 그래서 AI가 "아니다"라고 해도 규칙이 찾은 마감은 숨기지 않는다.
 * 숨기는 쪽(거부권)은 평가에서 수치로 비교한 뒤에만 켠다.
 */
public final class LocatorMerge {

    /** 한 공지에서 AI만 찾은 마감이 이보다 많으면 믿지 않고 전부 보류한다. 일정표를 통째로 마감으로 읽은 것이다. */
    static final int MAX_ADDED = 5;
    /** 행 제목의 길이 상한. 추출기와 같다. */
    private static final int LABEL_LIMIT = 36;

    /**
     * @param added 작은 모델이 찾았고 큰 모델이 확인해 공개하는 항목
     * @param held  작은 모델만 찾았거나 이름을 붙이지 못해 검수를 기다리는 항목
     * @param doubt 규칙이 할 일로 찾았는데 두 모델이 모두 아니라고 본 마감일
     */
    public record Result(List<ExtractedItem> added, List<ExtractedItem> held, Set<LocalDate> doubt) {
    }

    private LocatorMerge() {
    }

    /** 규칙이 학생의 할 일로 뽑은 마감일. */
    public static Set<LocalDate> ruleDeadlines(List<ExtractedItem> rule) {
        Set<LocalDate> out = new TreeSet<>();
        for (ExtractedItem i : rule) {
            if (ItemKind.ACTION.equals(ItemKind.of(i.title()))) {
                out.add(i.range().end());
            }
        }
        return out;
    }

    /** 한 번 읽은 결과에서 할 일이라고 한 마감일(action=true)이나 아니라고 한 마감일(action=false). */
    public static Set<LocalDate> dates(List<QuoteCheck.Located> reading, boolean action) {
        Set<LocalDate> out = new TreeSet<>();
        for (QuoteCheck.Located l : reading) {
            if (l.read() && l.found().action() == action) {
                deadlines(l).forEach(r -> out.add(r.end()));
            }
        }
        return out;
    }

    /**
     * 인용 하나가 가리키는 마감들. 파서가 읽은 날짜마다 하나다.
     * 다만 "8월 12일(수) 오후 1시 ~ 8월 18일(화)"처럼 시각이 끼어 파서가 둘로 읽은 기간은 하나로 잇는다. 뒤쪽 날짜의 글자가 "~"로 시작하면 앞 날짜의 끝이다.
     */
    static List<DateRange> deadlines(QuoteCheck.Located l) {
        List<DateRange> out = new ArrayList<>();
        for (DateRange r : l.ranges()) {
            if (r.start() == null && r.matchedText().startsWith("~") && !out.isEmpty()) {
                DateRange head = out.remove(out.size() - 1);
                LocalDate start = head.start() != null ? head.start() : head.end();
                out.add(new DateRange(start.isAfter(r.end()) ? null : start, r.end(), head.startsAt() != null ? head.startsAt() : head.endsAt(),
                        r.endsAt(), r.matchedText()));
            } else {
                out.add(r);
            }
        }
        return out;
    }

    /**
     * 작은 모델의 답이 화면을 바꾸려 하는가. 그럴 때만 큰 모델을 부른다.
     * 규칙이 못 찾은 마감을 할 일이라 했거나, 규칙이 찾은 마감을 아무도 할 일이라 하지 않고 누군가 아니라고 했을 때다.
     */
    public static boolean needsReview(List<ExtractedItem> rule, List<List<QuoteCheck.Located>> small) {
        Set<LocalDate> ruleDates = ruleDeadlines(rule);
        Set<LocalDate> yes = new TreeSet<>();
        Set<LocalDate> no = new TreeSet<>();
        for (List<QuoteCheck.Located> reading : small) {
            yes.addAll(dates(reading, true));
            no.addAll(dates(reading, false));
        }
        boolean adds = yes.stream().anyMatch(d -> !ruleDates.contains(d));
        boolean doubts = no.stream().anyMatch(d -> ruleDates.contains(d) && !yes.contains(d));
        return adds || doubts;
    }

    /**
     * @param small 작은 모델의 읽기들
     * @param large 큰 모델의 읽기. 부르지 않았거나 부르지 못했으면 null이고, 그러면 아무것도 공개하거나 의심하지 않는다
     */
    public static Result merge(NoticeView view, List<ExtractedItem> rule, List<List<QuoteCheck.Located>> small, List<QuoteCheck.Located> large) {
        Set<LocalDate> ruleDates = ruleDeadlines(rule);
        Set<LocalDate> yes = new TreeSet<>();
        Set<LocalDate> no = new TreeSet<>();
        for (List<QuoteCheck.Located> reading : small) {
            yes.addAll(dates(reading, true));
            no.addAll(dates(reading, false));
        }
        Set<LocalDate> largeYes = large == null ? Set.of() : dates(large, true);
        Set<LocalDate> largeNo = large == null ? Set.of() : dates(large, false);

        Set<LocalDate> doubt = new TreeSet<>();
        for (LocalDate d : ruleDates) {
            if (no.contains(d) && !yes.contains(d) && largeNo.contains(d) && !largeYes.contains(d)) {
                doubt.add(d);
            }
        }

        List<List<QuoteCheck.Located>> all = new ArrayList<>(small);
        if (large != null) {
            all.add(large);
        }
        List<ExtractedItem> added = new ArrayList<>();
        List<ExtractedItem> held = new ArrayList<>();
        for (LocalDate date : yes) {
            if (ruleDates.contains(date)) {
                continue;
            }
            ExtractedItem item = itemFor(view, date, all);
            if (item == null) {
                continue;
            }
            // 이름이 안내로 읽히면(ItemKind) 화면이 할 일로 올리지 않는다. 큰 모델이 맞다고 해도 보류한다.
            boolean named = ItemKind.ACTION.equals(ItemKind.of(item.title()));
            (largeYes.contains(date) && named ? added : held).add(item);
        }
        if (added.size() > MAX_ADDED) {
            held.addAll(added);
            added.clear();
        }
        return new Result(List.copyOf(added), List.copyOf(held), doubt);
    }

    /** 그 마감일을 가리킨 첫 인용으로 항목을 만든다. 날짜와 근거는 원문의 줄에서 온다. */
    private static ExtractedItem itemFor(NoticeView view, LocalDate date, List<List<QuoteCheck.Located>> readings) {
        for (List<QuoteCheck.Located> reading : readings) {
            for (QuoteCheck.Located l : reading) {
                if (!l.read() || !l.found().action()) {
                    continue;
                }
                DateRange range = deadlines(l).stream().filter(r -> r.end().equals(date)).findFirst().orElse(null);
                if (range == null) {
                    continue;
                }
                return new ExtractedItem(titleFor(view, l, range), range, DateItemExtractor.gradeOf(l.source()), "ai",
                        Evidence.of(l.source(), l.ranges(), range));
            }
        }
        return null;
    }

    /**
     * 이름은 규칙과 같은 방법으로 먼저 찾는다. 날짜 앞에 적힌 짧은 글("공결 신청 기한")이다. 없으면 AI가 적은 짧은 말, 그것도 없으면 공지 제목이다.
     * 날짜가 섞인 AI의 말은 쓰지 않는다. 화면에 나가는 날짜 글자는 원문에서만 온다.
     */
    static String titleFor(NoticeView view, QuoteCheck.Located l, DateRange range) {
        String lead = DateItemExtractor.leadOf(l.source(), range);
        if (lead.length() >= 2 && lead.length() <= LABEL_LIMIT && ItemKind.ACTION.equals(ItemKind.of(lead))) {
            return lead;
        }
        String what = l.found().what() == null ? "" : l.found().what().trim();
        if (what.length() >= 2 && what.length() <= 20 && what.chars().noneMatch(Character::isDigit)
                && ItemKind.ACTION.equals(ItemKind.of(what))) {
            return what;
        }
        return view.cleanTitle();
    }
}
