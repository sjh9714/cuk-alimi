package com.cukalimi.benefit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cukalimi.domain.BenefitSeed;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BenefitSeedFileTest {

    @Test
    void everyBenefitHasSourceVerifiedDateAndRenewalRule() {
        List<BenefitSeed> seeds = BenefitSeedFile.load();
        assertThat(seeds).hasSizeGreaterThanOrEqualTo(15);
        assertThat(seeds).allSatisfy(s -> {
            assertThat(s.sourceUrl()).startsWith("https://");
            assertThat(s.verifiedOn()).isBeforeOrEqualTo(LocalDate.now());
            assertThat(s.renewalRule()).isNotBlank();
            assertThat(s.summary().length()).isLessThanOrEqualTo(100);
        });
        assertThat(seeds.stream().map(BenefitSeed::slug).distinct().count()).isEqualTo(seeds.size());
    }

    @Test
    void rejectsEntryWithoutSource() {
        BenefitSeed bad = new BenefitSeed("no-source", "이름", "TOOL", "요약", "조건", "방법", null,
                "출처", "http://insecure.example", LocalDate.of(2026, 10, 26), "규칙", null);
        assertThatThrownBy(() -> BenefitSeedFile.validate(List.of(bad)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source_url");
    }
}
