package com.industrialhub.backend.security;

import com.industrialhub.backend.common.webhook.domain.WebhookInvalidUrlException;
import com.industrialhub.backend.common.webhook.service.WebhookUrlValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * SEC-106: WebhookUrlValidator blocks private/reserved IPs and handles malformed URLs.
 * DNS resolution with a 2-second timeout is tested structurally (actual DNS is not mocked
 * here — integration coverage is left to the service layer).
 */
class WebhookUrlValidatorTest {

    private final WebhookUrlValidator validator = new WebhookUrlValidator();

    @Test
    void shouldReject_malformedUrl() {
        assertThatThrownBy(() -> validator.validate("not-a-url"))
                .isInstanceOf(WebhookInvalidUrlException.class)
                .hasMessageContaining("URL inválida");
    }

    @Test
    void shouldReject_loopbackAddress() {
        // 127.0.0.1 is a loopback address — must be blocked
        assertThatThrownBy(() -> validator.validate("http://127.0.0.1/webhook"))
                .isInstanceOf(WebhookInvalidUrlException.class)
                .hasMessageContaining("privada");
    }

    @Test
    void shouldReject_localhostAlias() {
        // "localhost" resolves to loopback — must be blocked
        assertThatThrownBy(() -> validator.validate("http://localhost/webhook"))
                .isInstanceOf(WebhookInvalidUrlException.class)
                .hasMessageContaining("privada");
    }

    @Test
    void shouldReject_privateClassCAddress() {
        // 192.168.x.x is site-local — must be blocked (SEC-106)
        assertThatThrownBy(() -> validator.validate("http://192.168.1.50/hook"))
                .isInstanceOf(WebhookInvalidUrlException.class)
                .hasMessageContaining("privada");
    }

    @Test
    void shouldReject_privateClassBAddress() {
        // 10.x.x.x is site-local — must be blocked
        assertThatThrownBy(() -> validator.validate("http://10.0.0.1/hook"))
                .isInstanceOf(WebhookInvalidUrlException.class)
                .hasMessageContaining("privada");
    }

    @Test
    void shouldReject_awsMetadataEndpoint() {
        // 169.254.169.254 — AWS metadata service, must be blocked (link-local range)
        assertThatThrownBy(() -> validator.validate("http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(WebhookInvalidUrlException.class);
    }

    @Test
    void shouldReject_linkLocalAddress() {
        // 169.254.x.x range is link-local
        assertThatThrownBy(() -> validator.validate("http://169.254.0.1/hook"))
                .isInstanceOf(WebhookInvalidUrlException.class);
    }
}
