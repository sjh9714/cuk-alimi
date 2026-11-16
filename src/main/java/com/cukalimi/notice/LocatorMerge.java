package com.cukalimi.notice;

import com.cukalimi.dateparse.DateRange;
import com.cukalimi.feed.ItemKind;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 규칙이 뽑은 날짜와 AI가 찾은 문장을 합친다 (ADR 016). 운영 코드와 백테스트가 같은 함수를 쓴다.
 *
 * <p>단위는 공지 안의 마감일이다. AI는 같은 공지를 두 번 읽고, 둘이 엇갈리면 큰 모델이 한 번 더 읽는다. 표는 읽기마다 하나다.
 *
 * <table>
 * <tr><th>규칙</th><th>AI</th><th>결과</th></tr>
 * <tr><td>할 일로 찾음</td><td>할 일 또는 말 없음</td><td>그대로 보인다</td></tr>
 * <tr><td>할 일로 찾음</td><td>할 일이 아니라는 표가 모임</td><td>의심(doubt). 숨기지 않고 "확인"을 달아 보인다</td></tr>
 * <tr><td>못 찾음</td><td>할 일이라는 표가 모임</td><td>더한다(added). "확인"을 달아 보인다</td></tr>
 * <tr><td>못 찾음</td><td>한 표뿐</td><td>보류(held). 검수를 기다린다</td></tr>
 * </table>
 *
 * 놓치는 것이 잘못 보여 주는 것보다 아프다. 그래서 AI가 "아니다"라고 해도 규칙이 찾은 마감은 숨기지 않는다.
 * 숨기는 쪽(거부권)은 평가에서 수치로 비교한 뒤에만 켠다.
 */
public final class LocatorMerge {

    /** 한 공지에서 AI만 찾은 마감이 이보다 많으면 믿지 않고 전부 보류한다. 일정표를 통째로 마감으로 읽은 것이다. */
    static final int MAX_ADDED = 5;
    /** 행 제목의 길이 상한. 추출기와 같다. */
    private static final int LABEL_LIMIT = 36;

    /**
     * @param added AI만 찾았고 표가 모여 공개하는 항목
     * @param held  AI만 찾았지만 표가 모자라거나 이름을 붙이지 못해 검수를 기다리는 항목
     * @param doubt 규칙이 할 일로 찾았는데 AI가 아니라고 본 마감일
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
                l.ranges().forEach(r -> out.add(r.end()));
            }
        }
        return out;
    }

    /**
     * 두 읽기가 결과를 바꿀 만큼 엇갈렸는가. 그럴 때만 큰 모델을 부른다.
     * 규칙이 못 찾은 마감을 한쪽만 할 일이라 했거나, 규칙이 찾은 마감을 한쪽만 아니라고 했을 때다.
     * 규칙이 이미 찾은 마감을 둘 다 할 일이라 하든 한쪽만 하든 결과는 같으므로 부르지 않는다.
     */
    public static boolean disagree(List<ExtractedItem> rule, List<QuoteCheck.Located> first, List<QuoteCheck.Located> second) {
        Set<LocalDate> ruleDates = ruleDeadlines(rule);
        Set<LocalDate> yes = symmetricDifference(dates(first, true), dates(second, true));
        yes.removeAll(ruleDates);
        Set<LocalDate> no = symmetricDifference(dates(first, false), dates(second, false));
        no.retainAll(ruleDates);
        return !yes.isEmpty() || !no.isEmpty();
    }

    /**
     * @param readings 읽기 둘(작은 모델) 또는 셋(큰 모델이 한 번 더). 표가 둘 이상 모여야 한다
     */
    public static Result merge(NoticeView view, List<ExtractedItem> rule, List<List<QuoteCheck.Located>> readings) {
        Set<LocalDate> ruleDates = ruleDeadlines(rule);
        int need = 2;
        Map<LocalDate, Integer> yes = new LinkedHashMap<>();
        Map<LocalDate, Integer> no = new LinkedHashMap<>();
        for (List<QuoteCheck.Located> reading : readings) {
            dates(reading, true).forEach(d -> yes.merge(d, 1, Integer::sum));
            dates(reading, false).forEach(d -> no.merge(d, 1, Integer::sum));
        }

        Set<LocalDate> doubt = new TreeSet<>();
        for (LocalDate d : ruleDates) {
            if (no.getOrDefault(d, 0) >= need && yes.getOrDefault(d, 0) == 0) {
                doubt.add(d);
            }
        }

        List<ExtractedItem> added = new ArrayList<>();
        List<ExtractedItem> held = new ArrayList<>();
        for (Map.Entry<LocalDate, Integer> e : yes.entrySet()) {
            LocalDate date = e.getKey();
            if (ruleDates.contains(date)) {
                continue;
            }
            ExtractedItem item = itemFor(view, date, readings);
            if (item == null) {
                continue;
            }
            // 이름이 안내로 읽히면(ItemKind) 화면이 할 일로 올리지 않는다. 표가 모여도 보류한다.
            boolean named = ItemKind.ACTION.equals(ItemKind.of(item.title()));
            (e.getValue() >= need && named ? added : held).add(item);
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
                List<DateRange> hits = l.ranges().stream().filter(r -> r.end().equals(date)).toList();
                if (hits.isEmpty()) {
                    continue;
                }
                // 한 줄에 날짜가 여럿이고 끝만 적힌 날짜들이면 마감이 여럿이다. 추출기와 같이 합치지 않는다.
                boolean separate = l.ranges().size() > 1 && l.ranges().stream().allMatch(r -> r.start() == null);
                DateRange range = separate ? hits.get(0) : DateItemExtractor.merge(l.ranges());
                if (!range.end().equals(date)) {
                    range = hits.get(hits.size() - 1);
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

    private static Set<LocalDate> symmetricDifference(Set<LocalDate> a, Set<LocalDate> b) {
        Set<LocalDate> out = new TreeSet<>(a);
        out.addAll(b);
        Set<LocalDate> both = new TreeSet<>(a);
        both.retainAll(b);
        out.removeAll(both);
        return out;
    }
}
