package com.cukalimi.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.cukalimi.domain.Notice;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 학교가 공지를 고치면(기간 연장, 정정) 다시 읽고 다시 검수한다. */
class NoticeChangeTest {

    @Test
    void listTitleChangeTriggersRefetchButSpacingDoesNot() {
        String stored = "[학생지원팀] 선원가족장학생 선발 안내(~10.23)";
        assertThat(NoticeCollector.titleChanged(stored, "[학생지원팀] 선원가족장학생 선발 안내(~10.30 접수기간 연장)")).isTrue();
        assertThat(NoticeCollector.titleChanged(stored, "[학생지원팀]  선원가족장학생 선발 안내(~10.23)")).isFalse();
        assertThat(NoticeCollector.titleChanged(stored, "﻿" + stored)).isFalse();
        assertThat(NoticeCollector.titleChanged(stored, "")).isFalse();
    }

    @Test
    void changedContentGoesBackToTheReviewQueue() {
        Notice n = new Notice(1L, "장학", "선발 안내(~10.23)", LocalDate.of(2026, 10, 1), "", "u", "본문", false, "",
                Notice.REASON_PARSED);
        n.markReviewed("admin");

        assertThat(n.reparsed("선발 안내(~10.23)", "본문", false, "", Notice.REASON_PARSED)).isFalse();
        assertThat(n.getStatus()).isEqualTo(Notice.STATUS_REVIEWED);

        assertThat(n.reparsed("선발 안내(~10.30 접수기간 연장)", "본문", false, "", Notice.REASON_PARSED)).isTrue();
        assertThat(n.getStatus()).isEqualTo(Notice.STATUS_NEEDS_REVIEW);
    }
}
