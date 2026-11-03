package com.cukalimi.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AppUserTest {

    private static final LocalDate NOV_2026 = LocalDate.of(2026, 11, 2);

    @Test
    void 고르지_않으면_입학년도로_세고_초과학기에서_멈춘다() {
        AppUser user = profile(2021, null);
        assertThat(user.semester(NOV_2026)).isEqualTo(9);
        assertThat(user.grade(NOV_2026)).isEqualTo(4);
        assertThat(user.isSemesterManual()).isFalse();
        assertThat(profile(2026, null).semester(NOV_2026)).isEqualTo(2);
        assertThat(profile(2026, null).semester(LocalDate.of(2026, 4, 1))).isEqualTo(1);
    }

    @Test
    void 휴학으로_늦어진_학기를_고르면_그_학기와_학년이다() {
        AppUser user = profile(2021, 6);
        assertThat(user.semester(NOV_2026)).isEqualTo(6);
        assertThat(user.grade(NOV_2026)).isEqualTo(3);
        assertThat(user.autoSemester(NOV_2026)).isEqualTo(9);
        assertThat(user.isSemesterManual()).isTrue();
    }

    @Test
    void 고른_학기는_3월과_9월에_하나씩_오르고_초과학기에서_멈춘다() {
        AppUser user = profile(2021, 6);
        assertThat(user.semester(LocalDate.of(2027, 2, 28))).isEqualTo(6);
        assertThat(user.semester(LocalDate.of(2027, 3, 1))).isEqualTo(7);
        assertThat(user.grade(LocalDate.of(2027, 3, 1))).isEqualTo(4);
        assertThat(user.semester(LocalDate.of(2027, 9, 1))).isEqualTo(8);
        assertThat(user.semester(LocalDate.of(2028, 3, 1))).isEqualTo(9);
        assertThat(user.semester(LocalDate.of(2029, 3, 1))).isEqualTo(9);
    }

    @Test
    void 입학년도를_지우면_고른_학기도_사라진다() {
        AppUser user = profile(2021, 6);
        user.updateProfile(null, null, null, null, null, NOV_2026);
        assertThat(user.semester(NOV_2026)).isNull();
        assertThat(user.grade(NOV_2026)).isNull();
        assertThat(user.isSemesterManual()).isFalse();
    }

    private static AppUser profile(Integer entryYear, Integer semester) {
        AppUser user = new AppUser("student@catholic.ac.kr");
        user.updateProfile(entryYear, semester, null, null, null, NOV_2026);
        return user;
    }
}
