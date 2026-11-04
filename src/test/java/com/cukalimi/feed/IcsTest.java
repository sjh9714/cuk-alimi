package com.cukalimi.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 캘린더 구독 글 (ADR 004). 내게 맞는 카드는 모든 줄, 아니면 저장한 줄만 들어간다. */
class IcsTest {

    static Feed.Line line(long id, String label, LocalTime endsAt, boolean saved) {
        return new Feed.Line(id, label, null, LocalDate.of(2026, 7, 12), null, endsAt, "ACTION", saved);
    }

    static Feed.Card card(String title, boolean mine, Feed.Line deadline, Feed.Line... next) {
        return new Feed.Card("k", "scholarship", title, "원문 제목; 쉼표, 포함", "https://example.test/n/1",
                new Feed.AudienceView("GENERAL", null, null, null), mine, mine ? "3학년 대상" : null, deadline, List.of(next));
    }

    static Feed.View view(Feed.Card... cards) {
        return new Feed.View(List.of(new Feed.Section("must", "t", "n", false, List.of(cards))), true, true);
    }

    @Test
    void 내게_맞는_카드는_모든_줄이_저장한_카드는_그_줄만_들어간다() {
        String ics = Ics.of(view(
                card("내게 맞는 장학", true, line(1, null, null, false), line(2, "서류 발표", null, false)),
                card("저장만 한 공지", false, line(3, null, null, true), line(4, "안 저장한 줄", null, false)),
                card("남의 공지", false, line(5, null, null, false))), LocalDateTime.of(2026, 7, 10, 9, 0));
        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("UID:item-1@cuk-alimi").contains("UID:item-2@cuk-alimi").contains("UID:item-3@cuk-alimi")
                .doesNotContain("UID:item-4@").doesNotContain("UID:item-5@");
        assertThat(ics).contains("SUMMARY:내게 맞는 장학 · 서류 발표");
        // 시각 없는 마감은 그 날 하루 일정이다(DTEND는 다음 날).
        assertThat(ics).contains("DTSTART;VALUE=DATE:20260712").contains("DTEND;VALUE=DATE:20260713");
        // 긴 줄은 접히니 펴서 본다.
        assertThat(ics.replace("\r\n ", "")).contains("DESCRIPTION:3학년 대상\\n원문 제목\\; 쉼표\\, 포함\\nhttps://example.test/n/1");
    }

    @Test
    void 시각이_있으면_서울_시각을_UTC로_적는다() {
        String ics = Ics.of(view(card("17시 마감", true, line(9, null, LocalTime.of(17, 0), false))), LocalDateTime.of(2026, 7, 10, 9, 0));
        assertThat(ics).contains("DTSTART:20260712T070000Z").contains("DTEND:20260712T080000Z");
    }

    @Test
    void 긴_줄은_75옥텟에서_글자_경계로_접는다() {
        String folded = Ics.fold("SUMMARY:" + "가".repeat(60));
        for (String piece : folded.split("\r\n")) {
            assertThat(piece.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        assertThat(folded.replace("\r\n ", "")).isEqualTo("SUMMARY:" + "가".repeat(60));
    }
}
