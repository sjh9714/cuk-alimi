package com.cukalimi.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateItemTest {

    @Test
    void autoPublishedImageItemFollowsTheRuleNameWhenDatesStay() {
        LocalDate end = LocalDate.of(2026, 12, 9);
        DateItem item = DateItem.fromImage(1L, "notice:277473:img.table1.r1.c1", "수강신청", null, end, null, null, null);
        item.publishFromImage();

        item.refreshCandidate("겨울 계절학기 수강신청", null, end, null, null);
        assertThat(item.getTitle()).isEqualTo("겨울 계절학기 수강신청");

        // 날짜가 달라지면 공개한 날짜를 함부로 바꾸지 않는다. 그 판단은 두 번 읽기 일치 여부가 한다.
        item.refreshCandidate("다른 이름", null, end.plusDays(1), null, null);
        assertThat(item.getTitle()).isEqualTo("겨울 계절학기 수강신청");
        assertThat(item.getEndsOn()).isEqualTo(end);
    }
}
