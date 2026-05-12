package com.industrialhub.backend.common.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // Base64 of "industrialhub-msb-secret-key-for-testing-only" (>32 bytes)
    private static final String TEST_SECRET =
            "aW5kdXN0cmlhbGh1Yi1tc2Itc2VjcmV0LWtleS1mb3ItZGV2ZWxvcG1lbnQtb25seQ==";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3600000L); // 1h
    }

    @Test
    void generateToken_containsCorrectSubjectAndRole() {
        String token = jwtService.generateToken("operator", "OPERATOR");

        assertThat(jwtService.extractUsername(token)).isEqualTo("operator");
        assertThat(jwtService.extractRole(token)).isEqualTo("OPERATOR");
    }

    @Test
    void extractUsername_returnsSubjectFromToken() {
        String token = jwtService.generateToken("admin", "ADMIN");

        assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
    }

    @Test
    void extractRole_returnsRoleClaimFromToken() {
        String token = jwtService.generateToken("supervisor", "SUPERVISOR");

        assertThat(jwtService.extractRole(token)).isEqualTo("SUPERVISOR");
    }

    @Test
    void isTokenValid_trueForFreshToken() {
        String token = jwtService.generateToken("admin", "ADMIN");

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_falseForTamperedToken() {
        String token = jwtService.generateToken("admin", "ADMIN");
        String tampered = token.substring(0, token.length() - 4) + "xxxx";

        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void isTokenValid_throwsExpiredJwtExceptionForExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "expirationMs", 1L); // expires immediately
        String token = jwtService.generateToken("admin", "ADMIN");

        assertThatThrownBy(() -> jwtService.isTokenValid(token))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
