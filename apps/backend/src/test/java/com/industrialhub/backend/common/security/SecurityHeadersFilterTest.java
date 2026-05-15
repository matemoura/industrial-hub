package com.industrialhub.backend.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersFilterTest {

    private SecurityHeadersFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @Test
    void doFilterInternal_addsXContentTypeOptionsHeader() throws Exception {
        filter.doFilterInternal(request, response, chain);
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void doFilterInternal_addsXFrameOptionsHeader() throws Exception {
        filter.doFilterInternal(request, response, chain);
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    void doFilterInternal_addsStrictTransportSecurityHeader() throws Exception {
        filter.doFilterInternal(request, response, chain);
        assertThat(response.getHeader("Strict-Transport-Security"))
                .isEqualTo("max-age=31536000; includeSubDomains");
    }

    @Test
    void doFilterInternal_addsContentSecurityPolicyHeader() throws Exception {
        filter.doFilterInternal(request, response, chain);
        assertThat(response.getHeader("Content-Security-Policy"))
                .isEqualTo("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'");
    }

    @Test
    void doFilterInternal_addsReferrerPolicyHeader() throws Exception {
        filter.doFilterInternal(request, response, chain);
        assertThat(response.getHeader("Referrer-Policy"))
                .isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    void doFilterInternal_addsPermissionsPolicyHeader() throws Exception {
        filter.doFilterInternal(request, response, chain);
        assertThat(response.getHeader("Permissions-Policy"))
                .isEqualTo("camera=(), microphone=(), geolocation=()");
    }

    @Test
    void doFilterInternal_allSixHeadersPresent() throws Exception {
        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader("X-Content-Type-Options")).isNotNull();
        assertThat(response.getHeader("X-Frame-Options")).isNotNull();
        assertThat(response.getHeader("Strict-Transport-Security")).isNotNull();
        assertThat(response.getHeader("Content-Security-Policy")).isNotNull();
        assertThat(response.getHeader("Referrer-Policy")).isNotNull();
        assertThat(response.getHeader("Permissions-Policy")).isNotNull();
    }

    @Test
    void doFilterInternal_continuesFilterChain() throws Exception {
        filter.doFilterInternal(request, response, chain);
        // Se o chain não foi invocado, o request seria bloqueado — MockFilterChain valida isso
        assertThat(chain.getRequest()).isEqualTo(request);
    }
}
