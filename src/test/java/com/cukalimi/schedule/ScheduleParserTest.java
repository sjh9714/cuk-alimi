package com.cukalimi.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.cukalimi.Fixtures;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleParserTest {

    private final List<ScheduleEntry> entries = ScheduleParser.parse(Fixtures.read("calendar-2026.html"), 2026);

    @Test
    void readsCourseRegistration() {
        ScheduleEntry e = find("재학생 수강신청", LocalDate.of(2026, 8, 4));
        assertThat(e.endsOn()).isEqualTo(LocalDate.of(2026, 8, 7));
        assertThat(e.campuses()).containsExactly("성심");
    }

    @Test
    void readsCrossMonthRange() {
        ScheduleEntry e = find("수강신청 변경기간", LocalDate.of(2026, 8, 31));
        assertThat(e.endsOn()).isEqualTo(LocalDate.of(2026, 9, 4));
    }

    @Test
    void januaryAndFebruaryBelongToNextYear() {
        ScheduleEntry e = find("수강신청", LocalDate.of(2027, 2, 2));
        assertThat(e.endsOn()).isEqualTo(LocalDate.of(2027, 2, 4));
        ScheduleEntry leave = find("휴학원 제출기간", LocalDate.of(2027, 2, 10));
        assertThat(leave.endsOn()).isEqualTo(LocalDate.of(2027, 2, 26));
    }

    @Test
    void readsRegistrationPeriods() {
        assertThat(find("재학생 등록기간", LocalDate.of(2026, 8, 24)).endsOn()).isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(find("추가 등록기간", LocalDate.of(2026, 9, 7)).endsOn()).isEqualTo(LocalDate.of(2026, 9, 11));
    }

    @Test
    void everyEntryHasTitleAndCampus() {
        assertThat(entries).isNotEmpty();
        assertThat(entries).allSatisfy(e -> {
            assertThat(e.title()).isNotBlank();
            assertThat(e.campuses()).isNotEmpty();
            assertThat(e.endsOn()).isAfterOrEqualTo(e.startsOn());
        });
        assertThat(entries.stream().filter(e -> e.campuses().contains("성심")).count()).isGreaterThan(30);
    }

    private ScheduleEntry find(String title, LocalDate start) {
        return entries.stream()
                .filter(e -> e.title().equals(title) && e.startsOn().equals(start))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing " + title + " " + start + " in " + entries));
    }
}
