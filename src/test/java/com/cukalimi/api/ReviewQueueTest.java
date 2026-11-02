package com.cukalimi.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.cukalimi.domain.DateItem;
import com.cukalimi.domain.Notice;
import com.cukalimi.feed.Audience;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewQueueTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    private static Notice imageNotice() {
        return new Notice(1L, "장학", "[학생지원팀] 테스트 장학생 선발 안내", TODAY.minusDays(5), "", "https://example/1",
                "", true, "", Notice.REASON_IMAGE_ONLY);
    }

    @Test
    void pastCandidatesDoNotCloseTheNotice() {
        // 그림에서 읽은 후보가 모두 지난 날짜여도 운영자가 아직 보지 않았다. 학생 화면에는 "날짜 확인 중"으로 남아 있으니 큐에서도 빼지 않는다.
        Notice n = imageNotice();
        DateItem past = DateItem.fromImage(1L, "notice:1:img.line3", "공고일", null, TODAY.minusDays(3), null, null, null);
        assertThat(AdminController.closedReason(n, Audience.of(n), List.of(past), TODAY)).isNull();
    }

    @Test
    void pastSettledItemsCloseTheNotice() {
        Notice n = imageNotice();
        DateItem past = DateItem.fromNotice(1L, "notice:1:line3", "신청기간", null, TODAY.minusDays(3), null, null, null);
        assertThat(AdminController.closedReason(n, Audience.of(n), List.of(past), TODAY)).isEqualTo(AdminController.CLOSED_PAST);
    }
}
