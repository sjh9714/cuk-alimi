package com.cukalimi.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AppUserTest {

    private static final LocalDate NOV_2026 = LocalDate.of(2026, 11, 2);

    @Test
    void 고르지_않으면_입학년도로_세고_4학년에서_멈춘다() {
        AppUser user = profile(2021, null);
        assertThat(user.grade(NOV_2026)).isEqualTo(4);
        assertThat(user.autoGrade(NOV_2026)).isEqualTo(4);
        assertThat(user.isGradeManual()).isFalse();
    }

    @Test
    void 휴학으로_늦어진_학년을_고르면_그_학년이다() {
        AppUser user = profile(2021, 3);
        assertThat(user.grade(NOV_2026)).isEqualTo(3);
        assertThat(user.autoGrade(NOV_2026)).isEqualTo(4);
        assertThat(user.isGradeManual()).isTrue();
    }

    @Test
    void 고른_학년은_3월에_한_학년_오르고_4학년에서_멈춘다() {
        AppUser user = profile(2021, 3);
        assertThat(user.grade(LocalDate.of(2027, 2, 28))).isEqualTo(3);
        assertThat(user.grade(LocalDate.of(2027, 3, 1))).isEqualTo(4);
        assertThat(user.grade(LocalDate.of(2028, 3, 1))).isEqualTo(4);
    }

    @Test
    void 입학년도를_지우면_고른_학년도_사라진다() {
        AppUser user = profile(2021, 3);
        user.updateProfile(null, null, null, null, null, NOV_2026);
        assertThat(user.grade(NOV_2026)).isNull();
        assertThat(user.isGradeManual()).isFalse();
    }

    private static AppUser profile(Integer entryYear, Integer grade) {
        AppUser user = new AppUser("student@catholic.ac.kr");
        user.updateProfile(entryYear, grade, null, null, null, NOV_2026);
        return user;
    }
}
