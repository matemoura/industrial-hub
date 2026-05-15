package com.industrialhub.backend.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String DEV_SECRET_PLACEHOLDER =
            "dGVzdFNlY3JldEtleUZvckRldlVzZU9ubHlOb3RGb3JQcm9kdWN0aW9u";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms:28800000}")
    private long expirationMs;

    @PostConstruct
    void validateSecret() {
        if (secret.equals(DEV_SECRET_PLACEHOLDER)) {
            log.warn("SECURITY: JWT secret is using the dev placeholder. " +
                     "Set JWT_SECRET environment variable in production.");
        }
    }

    public String generateToken(String username, String role) {
        return generateToken(username, role, false);
    }

    public String generateToken(String username, String role, boolean mustChangePassword) {
        var builder = Jwts.builder()
                .subject(username)
                .claim("role", role)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey());
        if (mustChangePassword) {
            builder.claim("mustChangePassword", true);
        }
        return builder.compact();
    }

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            throw e;
        } catch (JwtException e) {
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
