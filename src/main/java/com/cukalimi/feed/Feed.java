package com.cukalimi.feed;

import com.cukalimi.domain.DateItem;
import com.cukalimi.domain.Notice;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.regex.Pattern;

/**
 * 홈 화면의 순서를 정한다 (ADR 009). 공지 한 건은 카드 한 장이고, 카드는 학생이 할 일(ACTION)이 남아 있을 때만 나온다.
 * 구간은 꼭 챙길 일정 → 내게 맞는 공지 → 장학·학사 공지 → 접힌 날짜 확인 중·취업·지역·특정 대상 순이다. 숨기는 것은 없고 접을 뿐이다.
 */
public final class Feed {

    /** 모든 재학생이 놓치면 손해인 공지. 학사일정 표의 항목과 같은 무게로 맨 위에 둔다. */
    private static final Pattern MUST_TITLE = Pattern.compile("국가장학금|국가근로|등록금|수강신청|수강과목\\s*취소|휴학|복학");

    public record Viewer(Integer grade, String major, String secondMajor, Regions.Region home) {
        public static final Viewer ANONYMOUS = new Viewer(null, null, null, null);
    }

    /** label은 공지 안에서 이 날짜가 무엇인지("트리니티 신청", "서류 합격자 발표"). 공지 제목과 같으면 비운다. */
    public record Line(long id, String label, LocalDate startsOn, LocalDate endsOn, LocalTime startsAt, LocalTime endsAt,
            String kind, boolean saved) {
    }

    /** grade는 제목과 자격 문단에 적힌 학년("2~4학년", "졸업예정자"). 없으면 null. */
    public record AudienceView(String scope, String label, String excerpt, String grade) {
        static AudienceView of(Audience a) {
            return new AudienceView(a.scope(), a.label(), a.excerpt(), a.grade().label());
        }
    }

    /**
     * kind는 schedule, scholarship, academic, career. deadline은 날짜 확인 중인 카드에서만 null이다.
     * mineReason은 내게 맞는 이유("3학년·컴퓨터정보공학부 대상"). mine일 때만 있다.
     */
    public record Card(String key, String kind, String title, String sourceTitle, String url, AudienceView audience,
            boolean mine, String mineReason, Line deadline, List<Line> next) {

        public Card(String key, String kind, String title, String sourceTitle, String url, AudienceView audience,
                boolean mine, Line deadline, List<Line> next) {
            this(key, kind, title, sourceTitle, url, audience, mine, null, deadline, next);
        }
    }

    public record Section(String key, String title, String note, boolean collapsed, List<Card> cards) {
    }

    public record View(List<Section> sections, boolean signedIn, boolean hasHomeRegion) {
    }

    private Feed() {
    }

    /**
     * @param upcoming    오늘 이후에 끝나는 날짜 항목
     * @param notices     항목이 가리키는 공지
     * @param audiences   공지 id별 대상 범위. undated의 공지도 들어 있다
     * @param undated     날짜가 그림에만 있어 운영자가 아직 날짜를 넣지 않은 공지 (ADR 003 규칙 2)
     * @param scheduleUrl 학년도별 학사일정 페이지 주소
     */
    public static View build(List<DateItem> upcoming, Map<Long, Notice> notices, Map<Long, Audience> audiences,
            List<Notice> undated, Viewer viewer, boolean signedIn, Set<Long> saved, IntFunction<String> scheduleUrl) {
        Map<String, List<DateItem>> groups = new LinkedHashMap<>();
        for (DateItem i : upcoming) {
            String key = i.getNoticeId() == null ? "item:" + i.getId() : "notice:" + i.getNoticeId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }

        List<Card> must = new ArrayList<>();
        List<Card> mine = new ArrayList<>();
        List<Card> open = new ArrayList<>();
        List<Card> career = new ArrayList<>();
        List<Card> region = new ArrayList<>();
        List<Card> group = new ArrayList<>();
        List<Card> events = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            List<DateItem> items = withoutTitleEcho(entry.getValue().stream().sorted(ITEM_ORDER).toList(), notices);
            DateItem first = items.get(0);
            Notice notice = first.getNoticeId() == null ? null : notices.get(first.getNoticeId());
            if (notice == null) {
                // 학사일정 표의 항목. 표가 곧 출처라 대상은 전체 학생이다.
                for (DateItem i : items) {
                    if (ItemKind.ACTION.equals(ItemKind.of(i.getTitle()))) {
                        must.add(new Card("item:" + i.getId(), "schedule", i.getTitle(), i.getTitle(),
                                scheduleUrl.apply(scheduleYear(i)), new AudienceView(Audience.GENERAL, null, null, null),
                                false, line(i, null, saved), List.of()));
                    } else if (isEvent(i, null)) {
                        events.add(new Card("item:" + i.getId(), "schedule", i.getTitle(), i.getTitle(),
                                scheduleUrl.apply(scheduleYear(i)), new AudienceView(Audience.GENERAL, null, null, null),
                                false, line(i, null, saved), List.of()));
                    }
                }
                continue;
            }
            Audience audience = audiences.get(notice.getId());
            if (audience == null || Audience.RESULT.equals(audience.scope())) {
                continue;
            }
            String title = Titles.clean(notice.getTitle());
            List<DateItem> actions = items.stream().filter(i -> ItemKind.ACTION.equals(ItemKind.of(i.getTitle()))).toList();
            // 모두가 알아 둘 행사는 할 일이 있든 없든 날짜를 따로 올린다. 취업 공지의 "일시"는 특강이 많아 올리지 않는다.
            if (Audience.GENERAL.equals(audience.scope())) {
                String named = "career".equals(kind(notice)) ? null : title;
                items.stream().filter(i -> isEvent(i, named)).forEach(i -> events.add(
                        new Card("event:" + i.getId(), kind(notice), ItemKind.isWhenOnly(i.getTitle()) ? title : tidyLabel(i.getTitle()),
                                notice.getTitle(), notice.getUrl(), AudienceView.of(audience), false, line(i, title, saved), List.of())));
            }
            if (actions.isEmpty()) {
                continue;
            }
            DateItem deadline = actions.get(0);
            List<Line> next = items.stream().filter(i -> i != deadline).map(i -> line(i, title, saved)).toList();
            String reason = mineReason(audience, deadline, viewer);
            boolean isMine = reason != null;
            Card card = new Card(entry.getKey(), kind(notice), title, notice.getTitle(), notice.getUrl(),
                    AudienceView.of(audience), isMine, reason, line(deadline, title, saved), next);
            // 취업 공지는 학년이 맞아도 접힌 구간에 둔다. "내게 맞는 공지"와 아침 메일은 장학·학사만 다룬다.
            if (isMust(audience, title)) {
                must.add(card);
            } else if ("career".equals(card.kind())) {
                career.add(card);
            } else if (isMine) {
                mine.add(card);
            } else if (Audience.REGION.equals(audience.scope())) {
                region.add(card);
            } else if (Audience.GROUP.equals(audience.scope()) || Audience.FIELD.equals(audience.scope())) {
                group.add(card);
            } else {
                open.add(card);
            }
        }

        List<Section> sections = new ArrayList<>();
        add(sections, "must", "꼭 챙길 일정", "모든 재학생이 대상이에요.", false, must);
        add(sections, "mine", "내게 맞는 공지", "연고 지역이나 학년·학과가 맞는 공지예요.", false, mine);
        add(sections, "open", "장학·학사 공지", "지원 자격은 카드의 원문 발췌를 확인하세요.", false, open);
        List<Card> waiting = undated.stream()
                .filter(n -> audiences.get(n.getId()) != null && !Audience.RESULT.equals(audiences.get(n.getId()).scope()))
                .sorted(Comparator.comparing(Notice::getPostedOn, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(n -> new Card("notice:" + n.getId(), kind(n), Titles.clean(n.getTitle()), n.getTitle(), n.getUrl(),
                        AudienceView.of(audiences.get(n.getId())), false, null, List.of()))
                .toList();
        if (!waiting.isEmpty()) {
            sections.add(new Section("undated", "날짜 확인 중", "날짜가 그림에만 있어 확인하고 있어요. 그동안은 원문에서 직접 확인하세요.",
                    true, waiting));
        }
        add(sections, "career", "취업·창업 공지", "채용과 취업 프로그램이에요. 학교 공지에 올라온 것만 모았어요.", true, career);
        add(sections, "region", viewer.home() == null ? "지역 장학" : "다른 지역 장학",
                viewer.home() == null ? "설정에서 연고 지역을 고르면 내 지역 장학이 위로 올라와요."
                        : "본인이나 부모님 주소가 그 지역이어야 해요.",
                true, region);
        add(sections, "group", "특정 대상·분야 장학", "특정 계층이나 전공만 신청할 수 있어요.", true, group);
        add(sections, "events", "행사 일정", "신청할 것 없이 날짜만 알아 두면 되는 행사예요.", true, events);
        return new View(sections, signedIn, viewer.home() != null);
    }

    /**
     * 제목에서 뽑은 항목("…선발 안내(~10.29)")과 본문 표에서 뽑은 항목("신청기간 ~10.29")이 같은 날이면 같은 마감이다.
     * 이름이 있는 쪽(본문)만 남긴다.
     */
    private static List<DateItem> withoutTitleEcho(List<DateItem> items, Map<Long, Notice> notices) {
        if (items.size() < 2 || items.get(0).getNoticeId() == null || notices.get(items.get(0).getNoticeId()) == null) {
            return items;
        }
        String title = Titles.clean(notices.get(items.get(0).getNoticeId()).getTitle());
        return items.stream()
                .filter(i -> !sameThing(i.getTitle(), title) || items.stream()
                        .noneMatch(o -> o != i && o.getEndsOn().equals(i.getEndsOn()) && !sameThing(o.getTitle(), title)))
                .toList();
    }

    private static boolean isEvent(DateItem i, String noticeTitle) {
        return ItemKind.isEvent(i.getTitle(), noticeTitle, i.getStartsOn() == null || i.getStartsOn().equals(i.getEndsOn()));
    }

    /** 모든 재학생이 챙겨야 하는 공지인가. 검수 큐도 이것부터 보여준다. */
    public static boolean isMust(Audience audience, String cleanTitle) {
        return Audience.GENERAL.equals(audience.scope()) && MUST_TITLE.matcher(cleanTitle).find();
    }

    /** 학사일정은 학년도 단위 페이지다. 2027년 2월 수강신청은 2026학년도 표에 있다. 키가 "schedule:2026:..." 꼴이다. */
    private static int scheduleYear(DateItem i) {
        String[] parts = i.getNaturalKey().split(":", 3);
        if (parts.length == 3 && parts[1].matches("\\d{4}")) {
            return Integer.parseInt(parts[1]);
        }
        return i.getEndsOn().getYear();
    }

    /**
     * 이 공지가 이 학생의 것이라는 이유. 아니면 null. AI가 읽은 대상 조건이 있으면 그것으로만 판단한다 (ADR 013).
     * 없으면(키가 없거나 두 번 읽기가 어긋남) 제목·자격 문단의 정규식과 운영자 태그로 판단한다.
     */
    public static String mineReason(Audience audience, DateItem deadline, Viewer viewer) {
        if (audience.ai() != null) {
            return audience.ai().reasonFor(viewer);
        }
        if (viewer.home() != null && Audience.REGION.equals(audience.scope())
                && audience.regions().stream().anyMatch(r -> r.covers(viewer.home()))) {
            return audience.label() + " 대상";
        }
        // 제목·자격 문단에 적힌 학년. 지역·계층 장학은 학년이 맞아도 그 조건이 먼저라 올리지 않는다.
        if (Audience.GENERAL.equals(audience.scope()) && audience.grade().covers(viewer.grade())) {
            return audience.grade().label() + " 대상";
        }
        // 운영자가 검수 때 학년·학과를 붙인 항목 (ADR 008). 둘 다 전체면 모두의 것이라 "내 것"으로 따로 올리지 않는다.
        if (deadline == null) {
            return null;
        }
        String g = deadline.getTargetGrade();
        String d = deadline.getTargetDept();
        if (g == null || d == null || ("ALL".equals(g) && "ALL".equals(d))) {
            return null;
        }
        boolean gradeOk = "ALL".equals(g) || (viewer.grade() != null && g.equals(String.valueOf(viewer.grade())));
        boolean deptOk = "ALL".equals(d) || contains(d, viewer.major()) || contains(d, viewer.secondMajor());
        if (!gradeOk || !deptOk) {
            return null;
        }
        return java.util.stream.Stream.of("ALL".equals(g) ? null : g + "학년", "ALL".equals(d) ? null : d)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining("·")) + " 대상";
    }

    private static String kind(Notice n) {
        return switch (n.getCategory() == null ? "" : n.getCategory()) {
            case "장학" -> "scholarship";
            case "취창업" -> "career";
            default -> "academic";
        };
    }

    private static boolean contains(String dept, String major) {
        return major != null && !major.isBlank() && dept.contains(major);
    }

    private static void add(List<Section> sections, String key, String title, String note, boolean collapsed, List<Card> cards) {
        if (!cards.isEmpty()) {
            sections.add(new Section(key, title, note, collapsed, cards.stream().sorted(CARD_ORDER).toList()));
        }
    }

    private static Line line(DateItem i, String noticeTitle, Set<Long> saved) {
        String label = noticeTitle == null || sameThing(i.getTitle(), noticeTitle) ? null : tidyLabel(i.getTitle());
        return new Line(i.getId(), label, i.getStartsOn(), i.getEndsOn(), i.getStartsAt(), i.getEndsAt(),
                ItemKind.of(i.getTitle()), saved.contains(i.getId()));
    }

    /** 표의 칸을 이어 붙인 이름("서강대학교 · 서강대학교 · 서울 · 첨부파일 참조")에서 겹친 칸과 안내 문구를 뺀다. */
    static String tidyLabel(String label) {
        List<String> parts = new ArrayList<>();
        for (String p : label.split("\\s*·\\s*")) {
            if (!p.isBlank() && !parts.contains(p) && !p.matches(".*(첨부|붙임|참조|참고).*")) {
                parts.add(p);
            }
        }
        return parts.isEmpty() ? label : String.join(" · ", parts);
    }

    /** 제목에서 뽑은 항목은 공지 제목과 같은 말이라 따로 적을 필요가 없다. */
    private static boolean sameThing(String itemTitle, String noticeTitle) {
        String a = Titles.clean(itemTitle).replaceAll("\\s", "");
        String b = noticeTitle.replaceAll("\\s", "");
        // "신청기간"은 "…추가신청기간 안내"에 들어 있어도 제목이 아니다. 길이가 제목에 가까울 때만 같은 말로 본다.
        return a.contains(b) || (b.contains(a) && a.length() * 10 >= b.length() * 6);
    }

    private static final Comparator<DateItem> ITEM_ORDER = Comparator.comparing(DateItem::getEndsOn)
            .thenComparing(i -> i.getEndsAt() == null ? LocalTime.MAX : i.getEndsAt())
            .thenComparing(DateItem::getTitle);

    private static final Comparator<Card> CARD_ORDER = Comparator.comparing((Card c) -> c.deadline().endsOn())
            .thenComparing(c -> c.deadline().endsAt() == null ? LocalTime.MAX : c.deadline().endsAt())
            .thenComparing(Card::title);
}
