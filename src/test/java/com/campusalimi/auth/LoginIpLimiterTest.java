package com.campusalimi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoginIpLimiterTest {

    private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");

    @Test
    void stopsAtTenAndOpensAfterAnHour() {
        LoginIpLimiter limiter = new LoginIpLimiter();
        for (int i = 0; i < LoginIpLimiter.HOURLY_PER_IP; i++) {
            assertThat(limiter.allow("10.0.0.1", NOW)).isTrue();
        }
        assertThat(limiter.allow("10.0.0.1", NOW)).isFalse();
        assertThat(limiter.allow("10.0.0.2", NOW)).isTrue();
        assertThat(limiter.allow("10.0.0.1", NOW.plus(Duration.ofMinutes(61)))).isTrue();
    }

    @Test
    void countsIpv6ByPrefix() {
        LoginIpLimiter limiter = new LoginIpLimiter();
        for (int i = 0; i < LoginIpLimiter.HOURLY_PER_IP; i++) {
            assertThat(limiter.allow("2001:db8:1:2::" + Integer.toHexString(i + 1), NOW)).isTrue();
        }
        assertThat(limiter.allow("2001:db8:1:2:ffff::1", NOW)).isFalse();
        assertThat(limiter.allow("2001:db8:1:3::1", NOW)).isTrue();
    }

    @Test
    void bracketedIpv6SharesThePrefix() {
        assertThat(LoginIpLimiter.keyOf("[2001:db8:1:2::1]")).isEqualTo(LoginIpLimiter.keyOf("2001:db8:1:2:ffff::9"));
    }

    @Test
    void fullTableForgetsTheLeastRecentIpInsteadOfRefusingNewOnes() {
        LoginIpLimiter limiter = new LoginIpLimiter();
        for (int i = 0; i < LoginIpLimiter.HOURLY_PER_IP; i++) {
            limiter.allow("first", NOW);
        }
        assertThat(limiter.allow("first", NOW)).isFalse();
        for (int i = 0; i < LoginIpLimiter.MAX_TRACKED; i++) {
            limiter.allow("ip-" + i, NOW);
        }

        assertThat(limiter.allow("newcomer", NOW)).isTrue();
        assertThat(limiter.allow("first", NOW)).isTrue();
    }
}
