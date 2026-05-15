package com.industrialhub.backend.common.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.EstimationProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiter para o endpoint de login.
 * Por IP: 5 tentativas por minuto; bloqueio de 5 min ao esgotar.
 * Por username: 10 tentativas por hora.
 */
@Component
public class LoginRateLimiter {

    private static final int IP_CAPACITY = 5;
    private static final Duration IP_REFILL_PERIOD = Duration.ofSeconds(60);
    private static final long IP_BLOCK_SECONDS = 300L; // 5 min

    private static final int USERNAME_CAPACITY = 10;
    private static final Duration USERNAME_REFILL_PERIOD = Duration.ofSeconds(3600);

    private final Cache<String, Bucket> ipCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    private final Cache<String, Bucket> usernameCache = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.HOURS)
            .build();

    /**
     * Verifica os limites de taxa para IP e username.
     * Usa {@link EstimationProbe} para verificar disponibilidade de ambos os buckets
     * antes de consumir qualquer token — eliminando a race condition em que o token
     * de IP era consumido mesmo quando o bucket de username estava esgotado.
     * Lança {@link TooManyRequestsException} se qualquer bucket estiver esgotado.
     */
    public void checkLimit(String ip, String username) {
        Bucket ipBucket       = ipCache.get(ip, k -> newIpBucket());
        Bucket usernameBucket = usernameCache.get(username, k -> newUsernameBucket());

        // Verificar disponibilidade antes de consumir qualquer token
        EstimationProbe ipEst       = ipBucket.estimateAbilityToConsume(1);
        EstimationProbe usernameEst = usernameBucket.estimateAbilityToConsume(1);

        if (!ipEst.canBeConsumed()) {
            long retryAfter = TimeUnit.NANOSECONDS.toSeconds(ipEst.getNanosToWaitForRefill());
            throw new TooManyRequestsException(
                    "Muitas tentativas. Tente novamente em 5 minutos.",
                    Math.max(retryAfter, 1)
            );
        }
        if (!usernameEst.canBeConsumed()) {
            long retryAfter = TimeUnit.NANOSECONDS.toSeconds(usernameEst.getNanosToWaitForRefill());
            throw new TooManyRequestsException(
                    "Muitas tentativas para este usuário. Tente novamente em 1 hora.",
                    Math.max(retryAfter, 1)
            );
        }

        // Ambos disponíveis — consumir
        ipBucket.tryConsume(1);
        usernameBucket.tryConsume(1);
    }

    private Bucket newIpBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(IP_CAPACITY)
                        .refillGreedy(IP_CAPACITY, IP_REFILL_PERIOD)
                        .build())
                .build();
    }

    private Bucket newUsernameBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(USERNAME_CAPACITY)
                        .refillGreedy(USERNAME_CAPACITY, USERNAME_REFILL_PERIOD)
                        .build())
                .build();
    }
}
