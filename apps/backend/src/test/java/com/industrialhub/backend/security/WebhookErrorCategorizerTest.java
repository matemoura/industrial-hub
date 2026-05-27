package com.industrialhub.backend.security;

import com.industrialhub.backend.common.webhook.service.WebhookErrorCategorizer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-101: verifies that WebhookErrorCategorizer returns safe generic messages
 * instead of leaking internal IPs, ports, or stack traces.
 */
class WebhookErrorCategorizerTest {

    private final WebhookErrorCategorizer categorizer = new WebhookErrorCategorizer();

    @Test
    void shouldReturnConnectionError_forConnectException() {
        assertThat(categorizer.categorize(new ConnectException("Connection refused: 10.0.0.5:8080")))
                .isEqualTo("Connection error");
    }

    @Test
    void shouldReturnTimeout_forSocketTimeoutException() {
        assertThat(categorizer.categorize(new SocketTimeoutException("Read timed out")))
                .isEqualTo("Timeout");
    }

    @Test
    void shouldReturnTimeout_forResourceAccessExceptionWrappingSocketTimeout() {
        ResourceAccessException ex = new ResourceAccessException("I/O error",
                new SocketTimeoutException("connect timed out"));
        assertThat(categorizer.categorize(ex)).isEqualTo("Timeout");
    }

    @Test
    void shouldReturnNetworkError_forResourceAccessException_withoutSocketTimeout() {
        ResourceAccessException ex = new ResourceAccessException("Connection reset");
        assertThat(categorizer.categorize(ex)).isEqualTo("Network error");
    }

    @Test
    void shouldReturnHttpError_forGenericException() {
        assertThat(categorizer.categorize(new RuntimeException("500 Internal Server Error")))
                .isEqualTo("HTTP error");
    }

    @Test
    void shouldNeverExposeInternalDetails_inReturnedMessage() {
        // The categorized message should never contain IPs, port numbers, or raw stack data
        String[] sensitiveTerms = {"10.0.0", "192.168", "172.16", "localhost", ":8080", "stack"};
        ConnectException connectEx = new ConnectException("Connection refused to 192.168.1.100:9090");
        String result = categorizer.categorize(connectEx);
        for (String term : sensitiveTerms) {
            assertThat(result).doesNotContain(term);
        }
    }
}
