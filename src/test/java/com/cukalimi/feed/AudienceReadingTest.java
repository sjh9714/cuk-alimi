package com.cukalimi.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.cukalimi.feed.AudienceReading.Kind;
import com.cukalimi.feed.AudienceReading.SemesterRule;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AI는 학기 문장의 종류와 숫자만 옮기고, 몇 번째 학기가 해당하는지는 코드가 센다 (ADR 013). */
class AudienceReadingTest {

    static AudienceReading reading(List<Integer> grades, SemesterRule... rules) {
        return new AudienceReading(grades, List.of(rules), List.of(), List.of(), List.of(), "");
    }

    static SemesterRule rule(Kind kind, int value) {
        return new SemesterRule(kind, value, 0);
    }

    @Test
    void 이수_학기와_재학_제한을_함께_센다() {
        // "4학기 이상 이수, 8학기 이상 재학 중인 자는 불가"
        AudienceFacts f = reading(List.of(), rule(Kind.COMPLETED_SEMESTERS_AT_LEAST, 4), rule(Kind.NOT_ENROLLED_AT_LEAST, 8)).toFacts();
        assertThat(f.semesters()).containsExactly(5, 6, 7);
        assertThat(f.grades()).isEmpty();
    }

    @Test
    void 진학_예정자는_지금_한_학기_전이다() {
        AudienceFacts f = reading(List.of(), new SemesterRule(Kind.ENTERING, 2, 1)).toFacts();
        assertThat(f.semesters()).containsExactly(2);
    }

    @Test
    void 학년_조건은_학기로_바꿔_함께_따진다() {
        // "1학년 이상 수료, 마지막 학기 불가"
        AudienceFacts f = reading(List.of(2, 3, 4), rule(Kind.NOT_FINAL_SEMESTER, 0)).toFacts();
        assertThat(f.semesters()).containsExactly(3, 4, 5, 6, 7);
    }

    @Test
    void 거의_모두가_해당하면_조건이_없다() {
        assertThat(reading(List.of(), rule(Kind.WITHIN_REGULAR, 0)).toFacts().restricted()).isFalse();
        assertThat(reading(List.of(), rule(Kind.ENROLLED_AT_LEAST, 1)).toFacts().restricted()).isFalse();
    }

    @Test
    void 학기_문장이_없으면_학년을_그대로_쓴다() {
        AudienceFacts f = reading(List.of(2, 3, 4)).toFacts();
        assertThat(f.grades()).containsExactly(2, 3, 4);
        assertThat(f.semesters()).isEmpty();
    }

    @Test
    void 맞는_학기가_없으면_아무에게도_칠하지_않는다() {
        AudienceFacts f = reading(List.of(1), rule(Kind.COMPLETED_SEMESTERS_AT_LEAST, 6)).toFacts();
        assertThat(f.restricted()).isFalse();
        assertThat(f.blockers()).containsExactly(AudienceFacts.Blocker.OTHER);
    }

    @Test
    void 졸업까지_남은_학기는_이번_학기를_포함해_센다() {
        AudienceFacts f = reading(List.of(), rule(Kind.COMPLETED_SEMESTERS_AT_LEAST, 1), rule(Kind.REMAINING_AT_LEAST, 2)).toFacts();
        assertThat(f.semesters()).containsExactly(2, 3, 4, 5, 6, 7);
    }
}
