package com.industrialhub.backend.common.webhook.service;

import com.industrialhub.backend.common.application.NotificationService;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookDeliveryRepository;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookSubscriptionRepository;
import com.industrialhub.backend.common.webhook.service.WebhookDispatchService;
import com.industrialhub.backend.common.webhook.service.WebhookSignatureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class WebhookDispatchServiceSecurityTest {

    @Mock private WebhookSubscriptionRepository subscriptionRepository;
    @Mock private WebhookDeliveryRepository deliveryRepository;
    @Mock private WebhookSignatureService signatureService;
    @Mock private RestTemplate webhookRestTemplate;
    @Mock private TaskScheduler taskScheduler;
    @Mock private NotificationService notificationService;

    private WebhookDispatchService service;

    @BeforeEach
    void setUp() {
        service = new WebhookDispatchService(
                subscriptionRepository, deliveryRepository, signatureService,
                webhookRestTemplate, taskScheduler, notificationService, new ObjectMapper());
    }

    @Test
    void sanitizeUrl_shouldRedactCredentials() {
        assertThat(service.sanitizeUrl("https://user:pass@host.com/hook"))
                .isEqualTo("https://***@host.com/hook");
    }

    @Test
    void sanitizeUrl_shouldReturnUnchanged_whenNoCredentials() {
        assertThat(service.sanitizeUrl("https://host.com/hook"))
                .isEqualTo("https://host.com/hook");
    }

    @Test
    void categorizeError_shouldReturn_ConnectionError_forConnectException() {
        assertThat(service.categorizeError(new ConnectException("refused")))
                .isEqualTo("Connection error");
    }

    @Test
    void categorizeError_shouldReturn_Timeout_forSocketTimeoutException() {
        assertThat(service.categorizeError(new SocketTimeoutException("timed out")))
                .isEqualTo("Timeout");
    }

    @Test
    void categorizeError_shouldReturn_Timeout_forResourceAccessWithSocketTimeout() {
        ResourceAccessException rae = new ResourceAccessException("timeout",
                new SocketTimeoutException("read timed out"));
        assertThat(service.categorizeError(rae)).isEqualTo("Timeout");
    }

    @Test
    void categorizeError_shouldReturn_NetworkError_forGenericResourceAccessException() {
        assertThat(service.categorizeError(new ResourceAccessException("network error")))
                .isEqualTo("Network error");
    }

    @Test
    void categorizeError_shouldReturn_HttpError_forOtherExceptions() {
        assertThat(service.categorizeError(new RuntimeException("algo interno")))
                .isEqualTo("HTTP error");
    }
}
