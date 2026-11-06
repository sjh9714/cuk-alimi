package com.cukalimi.feed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FeedAuditTest {

    @Test
    void symbolLeftFromTableCellsIsFlagged() {
        assertThat(FeedAudit.SYMBOL.matcher("수상 심의 필요>** IT관련 전국 및 교내외 대회 입상자").find()).isTrue();
        assertThat(FeedAudit.SYMBOL.matcher("졸업시험과목면제신청 – ●어학성적제출●").find()).isTrue();
        assertThat(FeedAudit.SYMBOL.matcher("ㆍ 참가신청").find()).isTrue();
        assertThat(FeedAudit.SYMBOL.matcher("하나장학생 트리니티 신청").find()).isFalse();
        assertThat(FeedAudit.SYMBOL.matcher("고지서 출력 및 수강료 납부 (등록)").find()).isFalse();
    }
}
