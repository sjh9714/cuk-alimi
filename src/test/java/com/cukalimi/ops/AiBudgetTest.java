package com.cukalimi.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 월 예산 가드 (ADR 016). 저장한 토큰에 단가를 곱해 세고, 상한에 닿으면 멈춘다. */
class AiBudgetTest {

    record Used(String model, long in, long out) implements AiBudget.Usage {
        public String getModel() {
            return model;
        }

        public long getInputTokens() {
            return in;
        }

        public long getOutputTokens() {
            return out;
        }
    }

    @Test
    void pricesFollowTheModel() {
        // Haiku 0.10 / 0.50, Opus 4 / 20 달러 (100만 토큰당).
        assertThat(AiPrices.usd("claude-haiku-5-5", 1_000_000, 1_000_000)).isCloseTo(0.60, within(1e-9));
        assertThat(AiPrices.usd("claude-opus-5-5", 1_000_000, 1_000_000)).isCloseTo(24.0, within(1e-9));
        // 두 번 읽기가 어긋난 공지는 모델 앞에 표시가 붙어 저장된다.
        assertThat(AiPrices.usd("disagree:claude-haiku-5-5", 2_000_000, 0)).isCloseTo(0.20, within(1e-9));
    }

    @Test
    void anUnknownModelIsCountedAtTheHighestPriceSoTheGuardNeverUndercounts() {
        assertThat(AiPrices.usd("some-new-model", 1_000_000, 0)).isCloseTo(4.0, within(1e-9));
        assertThat(AiPrices.usd(null, 1_000_000, 0)).isCloseTo(4.0, within(1e-9));
    }

    @Test
    void sumsUsageAcrossModels() {
        double usd = AiBudget.usd(List.of(new Used("claude-haiku-5-5", 3_000_000, 400_000), new Used("claude-opus-5-5", 500_000, 100_000)));
        assertThat(usd).isCloseTo(0.30 + 0.20 + 2.0 + 2.0, within(1e-9));
    }

    @Test
    void stopsWhenTheMonthReachesTheLimit() {
        assertThat(AiBudget.allowed(19.99, 20)).isTrue();
        assertThat(AiBudget.allowed(20.0, 20)).isFalse();
        assertThat(AiBudget.allowed(31.5, 20)).isFalse();
    }

    @Test
    void aLimitOfZeroTurnsTheGuardOff() {
        assertThat(AiBudget.allowed(1_000, 0)).isTrue();
    }
}
