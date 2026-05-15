package com.industrialhub.backend.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    private LoginRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new LoginRateLimiter();
    }

    @Test
    void ipBucket_allowsUpToFiveAttemptsWithoutThrowing() {
        // 5 tentativas devem passar sem exceção
        for (int i = 0; i < 5; i++) {
            final int attempt = i;
            assertThatCode(() -> rateLimiter.checkLimit("192.168.1.1", "user" + attempt))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void ipBucket_throwsOnSixthAttemptFromSameIp() {
        // Esgota 5 tokens do bucket de IP
        for (int i = 0; i < 5; i++) {
            rateLimiter.checkLimit("10.0.0.1", "differentUser" + i);
        }

        // 6ª tentativa deve lançar TooManyRequestsException
        assertThatThrownBy(() -> rateLimiter.checkLimit("10.0.0.1", "anotherUser"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Muitas tentativas");
    }

    @Test
    void ipBucket_blockReturnsCorrectRetryAfterSeconds() {
        // Esgota os 5 tokens do bucket de IP
        for (int i = 0; i < 5; i++) {
            rateLimiter.checkLimit("172.16.0.1", "user" + i);
        }

        TooManyRequestsException ex = org.junit.jupiter.api.Assertions.assertThrows(
                TooManyRequestsException.class,
                () -> rateLimiter.checkLimit("172.16.0.1", "user")
        );

        // Com refillGreedy(5, 60s): 1 token disponível a cada 12s.
        // O retryAfter dinâmico deve estar entre 1 e 60 segundos.
        org.assertj.core.api.Assertions.assertThat(ex.getRetryAfterSeconds())
                .isGreaterThanOrEqualTo(1L)
                .isLessThanOrEqualTo(60L);
    }

    @Test
    void usernameBucket_allowsUpToTenAttemptsFromDifferentIps() {
        // 10 tentativas do mesmo username mas IPs diferentes — bucket do username deve aguentar
        for (int i = 0; i < 10; i++) {
            final int attempt = i;
            assertThatCode(() -> rateLimiter.checkLimit("10.0.0." + attempt, "targetUser"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void usernameBucket_throwsOnEleventhAttemptForSameUsername() {
        // Usa IPs únicos para não esgotar o bucket de IP (cada IP tem seu próprio bucket)
        for (int i = 0; i < 10; i++) {
            rateLimiter.checkLimit("1.2.3." + i, "lockedUser");
        }

        // 11ª tentativa para o mesmo username (IP diferente, não esgota bucket de IP)
        assertThatThrownBy(() -> rateLimiter.checkLimit("1.2.3.100", "lockedUser"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Muitas tentativas");
    }

    @Test
    void usernameBucket_blockReturnsCorrectRetryAfterSeconds() {
        // Esgota os 10 tokens do bucket de username usando IPs únicos
        for (int i = 0; i < 10; i++) {
            rateLimiter.checkLimit("5.6.7." + i, "blockedUser");
        }

        // 11ª tentativa com IP diferente (não esgota bucket de IP) — deve lançar para username
        TooManyRequestsException ex = org.junit.jupiter.api.Assertions.assertThrows(
                TooManyRequestsException.class,
                () -> rateLimiter.checkLimit("5.6.7.100", "blockedUser")
        );

        // Com refillGreedy(10, 3600s): 1 token disponível a cada 360s.
        // O retryAfter dinâmico deve estar entre 1 e 3600 segundos.
        org.assertj.core.api.Assertions.assertThat(ex.getRetryAfterSeconds())
                .isGreaterThanOrEqualTo(1L)
                .isLessThanOrEqualTo(3600L);
    }

    @Test
    void differentIps_haveIndependentBuckets() {
        // Esgota bucket do IP 1
        for (int i = 0; i < 5; i++) {
            rateLimiter.checkLimit("192.168.1.1", "user" + i);
        }

        // IP 2 ainda deve passar sem problemas
        assertThatCode(() -> rateLimiter.checkLimit("192.168.1.2", "otherUser"))
                .doesNotThrowAnyException();
    }
}
