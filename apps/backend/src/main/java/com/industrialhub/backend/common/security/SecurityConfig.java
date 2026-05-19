package com.industrialhub.backend.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Value("${app.security.cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // OEE & worker reads: OPERATOR and above
                .requestMatchers(HttpMethod.GET, "/api/v1/oee/**", "/api/v1/workers/**")
                    .hasAnyRole("OPERATOR", "SUPERVISOR", "ADMIN")
                // OEE writes: SUPERVISOR and above
                .requestMatchers(HttpMethod.POST, "/api/v1/oee/**")
                    .hasAnyRole("SUPERVISOR", "ADMIN")
                // OEE Planned Downtime updates/deletes: SUPERVISOR and above
                .requestMatchers(HttpMethod.PUT, "/api/v1/oee/planned-downtimes/**")
                    .hasAnyRole("SUPERVISOR", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/oee/planned-downtimes/**")
                    .hasAnyRole("SUPERVISOR", "ADMIN")
                // QMS reads: OPERATOR and above
                .requestMatchers(HttpMethod.GET, "/api/v1/qms/**")
                    .hasAnyRole("OPERATOR", "SUPERVISOR", "ADMIN")
                // QMS NC creation: OPERATOR and above
                .requestMatchers(HttpMethod.POST, "/api/v1/qms/non-conformances")
                    .hasAnyRole("OPERATOR", "SUPERVISOR", "ADMIN")
                // QMS status transitions: SUPERVISOR and above
                .requestMatchers(HttpMethod.PUT, "/api/v1/qms/**")
                    .hasAnyRole("SUPERVISOR", "ADMIN")
                // User management: ADMIN only
                .requestMatchers("/api/v1/admin/users/**").hasRole("ADMIN")
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(securityHeadersFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityHeadersFilter securityHeadersFilter() {
        return new SecurityHeadersFilter();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
