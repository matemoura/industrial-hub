package com.industrialhub.backend.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adiciona os security headers definidos no ADR-021 em toda resposta HTTP.
 *
 * <p>Registrado explicitamente no {@link SecurityConfig} via
 * {@code addFilterBefore(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class)}
 * para garantir que os headers sejam adicionados mesmo em respostas 401/403 do Spring Security.
 * Não usa {@code @Component} para evitar registro duplo pelo
 * {@code DelegatingFilterProxy} do Spring Boot.</p>
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");

        filterChain.doFilter(request, response);
    }
}
