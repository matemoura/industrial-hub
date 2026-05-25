package com.industrialhub.backend.common.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.industrialhub.backend.common.application.NotificationService;
import com.industrialhub.backend.common.webhook.domain.DeliveryStatus;
import com.industrialhub.backend.common.webhook.domain.WebhookDelivery;
import com.industrialhub.backend.common.webhook.domain.WebhookEvent;
import com.industrialhub.backend.common.webhook.domain.WebhookSubscription;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookDeliveryRepository;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookSubscriptionRepository;
import com.industrialhub.backend.common.webhook.service.WebhookDispatchService;
import com.industrialhub.backend.common.webhook.service.WebhookSignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookDispatchServiceTest {

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

    private WebhookSubscription buildSubscription(String secret) {
        Set<WebhookEvent> events = new HashSet<>();
        events.add(WebhookEvent.NC_CREATED);
        return WebhookSubscription.builder()
                .id(UUID.randomUUID())
                .url("https://example.com/hook")
                .secret(secret)
                .events(events)
                .active(true)
                .description("test")
                .createdBy("admin")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private WebhookDelivery buildDelivery(WebhookSubscription sub) {
        return WebhookDelivery.builder()
                .id(UUID.randomUUID())
                .subscription(sub)
                .event(WebhookEvent.NC_CREATED)
                .attempt(1)
                .status(DeliveryStatus.PENDING_RETRY)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void sendWithRetry_onSuccess_setsDeliveryStatusToSuccess() {
        WebhookSubscription sub = buildSubscription(null);
        WebhookDelivery delivery = buildDelivery(sub);

        when(deliveryRepository.save(any())).thenReturn(delivery);
        when(webhookRestTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("OK"));

        service.sendWithRetry(sub, WebhookEvent.NC_CREATED, "payload", 1);

        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryRepository, atLeast(2)).save(captor.capture());
        // Last save should be SUCCESS
        WebhookDelivery lastSaved = captor.getAllValues().stream()
                .filter(d -> d.getStatus() == DeliveryStatus.SUCCESS)
                .findFirst()
                .orElse(null);
        assertThat(lastSaved).isNotNull();
    }

    @Test
    void sendWithRetry_onFirstFailure_scheduleRetryAndSetsPendingRetry() {
        WebhookSubscription sub = buildSubscription(null);
        WebhookDelivery delivery = buildDelivery(sub);

        when(deliveryRepository.save(any())).thenReturn(delivery);
        when(webhookRestTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        service.sendWithRetry(sub, WebhookEvent.NC_CREATED, "payload", 1);

        verify(taskScheduler).schedule(any(Runnable.class), any(java.time.Instant.class));
        // Subscription should still be active
        assertThat(sub.isActive()).isTrue();
    }

    @Test
    void sendWithRetry_afterThreeFailures_disablesSubscriptionAndNotifiesAdmin() {
        WebhookSubscription sub = buildSubscription(null);
        WebhookDelivery delivery = buildDelivery(sub);

        when(deliveryRepository.save(any())).thenReturn(delivery);
        when(webhookRestTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        service.sendWithRetry(sub, WebhookEvent.NC_CREATED, "payload", 3);

        assertThat(sub.isActive()).isFalse();
        assertThat(sub.getDisabledAt()).isNotNull();
        verify(subscriptionRepository).save(sub);
        verify(notificationService).broadcast(anyString(), anyString(), any());
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(java.time.Instant.class));
    }

    @Test
    void sendWithRetry_withSecret_setsSignatureHeader() {
        WebhookSubscription sub = buildSubscription("my-secret");
        WebhookDelivery delivery = buildDelivery(sub);

        when(deliveryRepository.save(any())).thenReturn(delivery);
        when(signatureService.sign(anyString(), anyString())).thenReturn("sha256=abc123");
        when(webhookRestTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("OK"));

        service.sendWithRetry(sub, WebhookEvent.NC_CREATED, "payload", 1);

        verify(signatureService).sign(eq("my-secret"), anyString());
    }

    @Test
    void sendWithRetry_backoffDelays_correctValues() {
        WebhookSubscription sub = buildSubscription(null);
        WebhookDelivery delivery = buildDelivery(sub);
        when(deliveryRepository.save(any())).thenReturn(delivery);
        when(webhookRestTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("err"));

        ArgumentCaptor<java.time.Instant> instantCaptor = ArgumentCaptor.forClass(java.time.Instant.class);

        // Attempt 1: delay ~5s
        java.time.Instant before1 = java.time.Instant.now();
        service.sendWithRetry(sub, WebhookEvent.NC_CREATED, "payload", 1);
        verify(taskScheduler).schedule(any(Runnable.class), instantCaptor.capture());
        java.time.Instant scheduled1 = instantCaptor.getValue();
        long delay1Ms = scheduled1.toEpochMilli() - before1.toEpochMilli();
        assertThat(delay1Ms).isBetween(4000L, 6000L);

        reset(taskScheduler);
        instantCaptor = ArgumentCaptor.forClass(java.time.Instant.class);

        // Attempt 2: delay ~30s
        java.time.Instant before2 = java.time.Instant.now();
        service.sendWithRetry(sub, WebhookEvent.NC_CREATED, "payload", 2);
        verify(taskScheduler).schedule(any(Runnable.class), instantCaptor.capture());
        java.time.Instant scheduled2 = instantCaptor.getValue();
        long delay2Ms = scheduled2.toEpochMilli() - before2.toEpochMilli();
        assertThat(delay2Ms).isBetween(29000L, 31000L);
    }
}
