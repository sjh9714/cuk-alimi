package com.cukalimi.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.cukalimi.dateparse.DateRange;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageAgreementTest {

    private static final LocalDate POSTED = LocalDate.of(2026, 10, 28);
    private static final LocalDate DAY = LocalDate.of(2026, 11, 4);

    private static ExtractedItem item(String title, LocalDate end) {
        return new ExtractedItem(title, new DateRange(end, end, null, null, ""), null, "line1");
    }

    @Test
    void lectureDayReadTheSameTwiceGoesPublic() {
        var first = List.of(item("일시", DAY));
        assertThat(ImageNoticeReader.agreedDates(first, first, POSTED, "2026학년도 인간학 가을 특강 안내")).containsExactly(DAY);
    }

    @Test
    void eventDaysThatDifferStayHidden() {
        assertThat(ImageNoticeReader.agreedDates(List.of(item("일시", DAY)), List.of(item("일시", DAY.plusDays(1))), POSTED, "가을 특강 안내")).isEmpty();
    }

    @Test
    void plainDatesWithoutAnEventNameStayHidden() {
        var first = List.of(item("일시", DAY));
        assertThat(ImageNoticeReader.agreedDates(first, first, POSTED, "교내 안내문")).isEmpty();
    }

    @Test
    void eventBeforeTheNoticeDateStaysHidden() {
        var first = List.of(item("일시", POSTED.minusDays(2)));
        assertThat(ImageNoticeReader.agreedDates(first, first, POSTED, "가을 특강 안내")).isEmpty();
    }

    @Test
    void differingActionDatesBlockTheWholeNotice() {
        var first = List.of(item("신청기간", DAY), item("장학증서 수여식", DAY.plusDays(20)));
        var second = List.of(item("신청기간", DAY.plusDays(1)), item("장학증서 수여식", DAY.plusDays(20)));
        assertThat(ImageNoticeReader.agreedDates(first, second, POSTED, "장학생 선발 안내")).isEmpty();
    }
}
