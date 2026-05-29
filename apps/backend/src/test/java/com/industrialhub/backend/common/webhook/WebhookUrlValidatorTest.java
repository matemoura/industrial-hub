package com.industrialhub.backend.common.webhook;

import com.industrialhub.backend.common.webhook.domain.WebhookInvalidUrlException;
import com.industrialhub.backend.common.webhook.service.WebhookUrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-106 SEC: @Tag("requires-network") — excluir em CI sem DNS externo via:
 *   ./mvnw test -Pci   (profile surefire com excludedGroups=requires-network)
 * validate_publicUrl_doesNotThrow faz resolução DNS real para hooks.slack.com.
 */
@Tag("requires-network")
class WebhookUrlValidatorTest {

    private WebhookUrlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WebhookUrlValidator();
    }

    @Test
    void validate_publicUrl_doesNotThrow() {
        // Uses a well-known public hostname — DNS resolution required in test env
        // hooks.example.com may not resolve; use a known resolvable public host
        assertThatNoException().isThrownBy(() -> validator.validate("https://hooks.slack.com/endpoint"));
    }

    @Test
    void validate_loopback_throwsWebhookInvalidUrlException() {
        assertThatThrownBy(() -> validator.validate("https://127.0.0.1/hook"))
                .isInstanceOf(WebhookInvalidUrlException.class)
                .hasMessageContaining("127.0.0.1");
    }

    @Test
    void validate_rfc1918_throwsWebhookInvalidUrlException() {
        assertThatThrownBy(() -> validator.validate("https://192.168.1.10/hook"))
                .isInstanceOf(WebhookInvalidUrlException.class)
                .hasMessageContaining("192.168.1.10");
    }

    @Test
    void validate_linkLocal_awsMetadata_throwsWebhookInvalidUrlException() {
        assertThatThrownBy(() -> validator.validate("https://169.254.169.254/latest/meta-data"))
                .isInstanceOf(WebhookInvalidUrlException.class)
                .hasMessageContaining("169.254.169.254");
    }

    @Test
    void validate_malformedUrl_throwsWebhookInvalidUrlException() {
        assertThatThrownBy(() -> validator.validate("not-a-valid-url"))
                .isInstanceOf(WebhookInvalidUrlException.class)
                .hasMessageContaining("URL inválida");
    }
}
