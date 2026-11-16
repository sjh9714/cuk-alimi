package com.cukalimi.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 근거는 날짜가 같을 때만 따라간다 (ADR 016). 사람이 날짜를 고친 항목에 옛 날짜가 적힌 글을 보여 주지 않는다. */
class DateItemEvidenceTest {

    private static final LocalDate OCT_23 = LocalDate.of(2026, 10, 23);
    private static final LocalDate OCT_30 = LocalDate.of(2026, 10, 30);

    private static DateItem extracted(LocalDate end, String text, String match) {
        return DateItem.fromNotice(1L, "notice:1:t:신청#1", "신청", null, end, null, null, null).withEvidence(text, match);
    }

    @Test
    void anAutomaticRowTakesTheNewEvidenceWhenTheSchoolMovesTheDeadline() {
        DateItem row = extracted(OCT_23, "신청기간 : ~ 10. 23.(금)", "10. 23.(금)");
        DateItem fresh = extracted(OCT_30, "신청기간 : ~ 10. 30.(금) 연장", "10. 30.(금)");

        assertThat(row.refreshFrom(fresh)).isTrue();
        row.followEvidence(fresh);

        assertThat(row.getEndsOn()).isEqualTo(OCT_30);
        assertThat(row.getEvidenceMatch()).isEqualTo("10. 30.(금)");
    }

    @Test
    void aReviewedRowKeepsEvidenceOnlyWhileItsDateStillMatchesTheNotice() {
        DateItem row = extracted(OCT_23, "신청기간 : ~ 10. 23.(금)", "10. 23.(금)");
        row.markReviewed("admin");

        DateItem same = extracted(OCT_23, "신청 기간 : ~ 10. 23.(금)까지", "10. 23.(금)까지");
        assertThat(row.refreshFrom(same)).isFalse();
        row.followEvidence(same);
        assertThat(row.getEvidenceText()).isEqualTo("신청 기간 : ~ 10. 23.(금)까지");

        DateItem moved = extracted(OCT_30, "신청기간 : ~ 10. 30.(금) 연장", "10. 30.(금)");
        assertThat(row.refreshFrom(moved)).isFalse();
        row.followEvidence(moved);
        assertThat(row.getEndsOn()).isEqualTo(OCT_23);
        assertThat(row.getEvidenceText()).isNull();
        assertThat(row.getEvidenceMatch()).isNull();
    }

    @Test
    void editingTheDateByHandDropsTheOldEvidence() {
        DateItem row = extracted(OCT_23, "신청기간 : ~ 10. 23.(금)", "10. 23.(금)");

        row.review("신청", null, OCT_30, null, null, null, null, null, "admin");

        assertThat(row.getEvidenceText()).isNull();
    }

    @Test
    void confirmingWithoutChangingTheDateKeepsTheEvidence() {
        DateItem row = extracted(OCT_23, "신청기간 : ~ 10. 23.(금)", "10. 23.(금)");

        row.review("장학 신청", null, OCT_23, null, null, null, null, null, "admin");

        assertThat(row.getEvidenceMatch()).isEqualTo("10. 23.(금)");
    }
}
