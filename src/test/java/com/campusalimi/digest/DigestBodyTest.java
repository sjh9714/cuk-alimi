package com.campusalimi.digest;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusalimi.config.AppProperties;
import com.campusalimi.domain.AppUser;
import com.campusalimi.feed.Audience;
import com.campusalimi.feed.Feed;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 메일 본문과 멱등 키. DB 없이 본다. */
class DigestBodyTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    static Feed.Card card(String key, String title, Feed.Line deadline) {
        return new Feed.Card(key, "scholarship", title, title, "https://school/" + key,
                new Feed.AudienceView(Audience.GENERAL, null, null, null), false, deadline, List.of());
    }

    static Feed.Line line(long id, String label, LocalDate ends, LocalTime at, boolean saved) {
        return new Feed.Line(id, label, null, ends, null, at, "ACTION", saved);
    }

    DigestService service() {
        AppProperties props = new AppProperties("https://www.catholic.ac.kr", 0, null, "https://campus-alimi.example", "",
                "", true, new AppProperties.Mail("console", "campus@example"), new AppProperties.Digest("0 0 8 * * *"),
                null, null, null);
        return new DigestService(props, null, null, null, null, null, null, null, Clock.systemUTC());
    }

    @Test
    void bodyNamesEachEventWithDdayAndEndsWithUnsubscribeLink() {
        Feed.Card work = card("notice:1", "국가근로장학금 추가신청기간 안내", line(10, "신청기간", TODAY.plusDays(1), LocalTime.of(18, 0), false));
        Feed.Card saved = card("notice:2", "인송장학생 선발 안내", line(11, "원본서류 제출", TODAY, null, true));
        DigestService.Digest d = new DigestService.Digest(List.of(work),
                List.of(new DigestService.SavedLine(saved, saved.deadline())), List.of());

        String body = service().body(new AppUser("s@catholic.ac.kr"), d, TODAY);
        assertThat(body).contains("■ 새로 올라온 공지 1")
                .contains("- 10.29(목) D-1 · 국가근로장학금 추가신청기간 안내 · 신청기간 18:00")
                .contains("■ 저장한 마감 3일 안 1")
                .contains("- 10.28(수) 오늘 · 인송장학생 선발 안내 · 원본서류 제출")
                .contains("https://school/notice:1")
                .contains("/api/digest/unsubscribe?token=");
        assertThat(d.keys()).containsExactly("notice:1", "saved:11");
        assertThat(DigestService.subject(TODAY)).isEqualTo("[CUK-Alimi] 10월 28일 아침 알림");
    }

    @Test
    void emptyDigestIsNotSent() {
        assertThat(new DigestService.Digest(List.of(), List.of(), List.of()).isEmpty()).isTrue();
    }
}
