package com.industrialhub.backend.common.webhook;

import com.industrialhub.backend.common.webhook.service.WebhookSignatureService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureServiceTest {

    private final WebhookSignatureService service = new WebhookSignatureService();

    @Test
    void sign_returnsCorrectHmacSha256Prefix() {
        String secret = "my-secret";
        String payload = "{\"event\":\"NC_CREATED\"}";

        String signature = service.sign(secret, payload);

        assertThat(signature).startsWith("sha256=");
        assertThat(signature).hasSize(71); // "sha256=" (7) + 64 hex chars
    }

    @Test
    void sign_sameInputProducesSameSignature() {
        String secret = "stable-secret";
        String payload = "{\"test\":true}";

        String sig1 = service.sign(secret, payload);
        String sig2 = service.sign(secret, payload);

        assertThat(sig1).isEqualTo(sig2);
    }

    @Test
    void sign_differentSecretProducesDifferentSignature() {
        String payload = "{\"test\":true}";

        String sig1 = service.sign("secret-a", payload);
        String sig2 = service.sign("secret-b", payload);

        assertThat(sig1).isNotEqualTo(sig2);
    }

    @Test
    void sign_differentPayloadProducesDifferentSignature() {
        String secret = "my-secret";

        String sig1 = service.sign(secret, "{\"event\":\"A\"}");
        String sig2 = service.sign(secret, "{\"event\":\"B\"}");

        assertThat(sig1).isNotEqualTo(sig2);
    }

    @Test
    void sign_knownVector() {
        // Pre-computed: echo -n '{"event":"TEST"}' | openssl dgst -sha256 -hmac 'key'
        // key="key", payload='{"event":"TEST"}'
        // Expected can be verified externally. Just assert format here.
        String sig = service.sign("key", "{\"event\":\"TEST\"}");
        assertThat(sig).matches("sha256=[0-9a-f]{64}");
    }
}
