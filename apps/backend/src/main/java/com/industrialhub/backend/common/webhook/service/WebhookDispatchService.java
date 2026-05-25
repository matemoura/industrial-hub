package com.industrialhub.backend.common.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.industrialhub.backend.common.application.NotificationService;
import com.industrialhub.backend.common.domain.NotificationSeverity;
import com.industrialhub.backend.common.webhook.domain.DeliveryStatus;
import com.industrialhub.backend.common.webhook.domain.WebhookDelivery;
import com.industrialhub.backend.common.webhook.domain.WebhookEvent;
import com.industrialhub.backend.common.webhook.domain.WebhookSubscription;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookDeliveryRepository;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class WebhookDispatchService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatchService.class);

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSignatureService signatureService;
    private final RestTemplate webhookRestTemplate;
    private final TaskScheduler taskScheduler;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public WebhookDispatchService(
            WebhookSubscriptionRepository subscriptionRepository,
            WebhookDeliveryRepository deliveryRepository,
            WebhookSignatureService signatureService,
            @Qualifier("webhookRestTemplate") RestTemplate webhookRestTemplate,
            TaskScheduler taskScheduler,
            NotificationService notificationService,
            ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.signatureService = signatureService;
        this.webhookRestTemplate = webhookRestTemplate;
        this.taskScheduler = taskScheduler;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Async
    public void dispatch(WebhookEvent event, Object entityPayload) {
        List<WebhookSubscription> targets = subscriptionRepository
                .findByActiveAndEventsContaining(true, event);

        for (WebhookSubscription sub : targets) {
            sendWithRetry(sub, event, entityPayload, 1);
        }
    }

    public void sendWithRetry(WebhookSubscription sub, WebhookEvent event, Object entityPayload, int attempt) {
        String payloadJson;
        try {
            payloadJson = buildPayloadJson(event, entityPayload);
        } catch (Exception e) {
            log.error("Falha ao serializar payload para webhook {}: {}", sub.getId(), e.getMessage());
            return;
        }

        WebhookDelivery delivery = WebhookDelivery.builder()
                .subscription(sub)
                .event(event)
                .attempt(attempt)
                .status(DeliveryStatus.PENDING_RETRY)
                .createdAt(LocalDateTime.now())
                .build();
        delivery = deliveryRepository.save(delivery);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (sub.getSecret() != null && !sub.getSecret().isBlank()) {
                headers.set("X-Hub-Signature-256", signatureService.sign(sub.getSecret(), payloadJson));
            }
            headers.set("X-Industrial-Hub-Event", event.name());
            headers.set("X-Industrial-Hub-Delivery", delivery.getId().toString());

            long start = System.currentTimeMillis();
            ResponseEntity<String> response = webhookRestTemplate.postForEntity(
                    sub.getUrl(), new HttpEntity<>(payloadJson, headers), String.class);
            long duration = System.currentTimeMillis() - start;

            if (response.getStatusCode().is2xxSuccessful()) {
                delivery.setStatus(DeliveryStatus.SUCCESS);
                delivery.setResponseCode(response.getStatusCode().value());
                delivery.setDurationMs(duration);
                deliveryRepository.save(delivery);
            } else {
                int code = response.getStatusCode().value();
                handleFailure(sub, event, entityPayload, delivery, attempt, code, duration, null);
            }
        } catch (Exception e) {
            handleFailure(sub, event, entityPayload, delivery, attempt, null, 0L, e.getMessage());
        }
    }

    private void handleFailure(WebhookSubscription sub, WebhookEvent event, Object entityPayload,
                                WebhookDelivery delivery, int attempt,
                                Integer responseCode, long duration, String error) {
        boolean willRetry = attempt < 3;

        delivery.setStatus(willRetry ? DeliveryStatus.PENDING_RETRY : DeliveryStatus.FAILED);
        delivery.setResponseCode(responseCode);
        delivery.setDurationMs(duration);
        delivery.setErrorMessage(error);
        deliveryRepository.save(delivery);

        if (willRetry) {
            long delayMs = switch (attempt) {
                case 1 -> 5_000L;
                case 2 -> 30_000L;
                default -> 120_000L;
            };
            final WebhookDelivery finalDelivery = delivery;
            taskScheduler.schedule(
                    () -> sendWithRetry(sub, event, entityPayload, attempt + 1),
                    Instant.now().plusMillis(delayMs)
            );
            log.warn("Webhook falhou (tentativa {}/3) para {}: {}. Retry em {}ms",
                    attempt, sub.getUrl(), error, delayMs);
        } else {
            // 3ª falha: desativar subscription e notificar ADMIN
            sub.setActive(false);
            sub.setDisabledAt(LocalDateTime.now());
            subscriptionRepository.save(sub);

            String title = "Webhook desativado após 3 falhas";
            String body = String.format(
                    "A subscription '%s' (%s) foi desativada automaticamente após 3 tentativas consecutivas sem resposta 2xx.",
                    sub.getDescription() != null ? sub.getDescription() : sub.getId().toString(),
                    sub.getUrl());
            notificationService.broadcast(title, body, NotificationSeverity.CRITICAL);

            log.error("Webhook desativado após 3 falhas: subscriptionId={}, url={}", sub.getId(), sub.getUrl());
        }
    }

    private String buildPayloadJson(WebhookEvent event, Object entityPayload) {
        try {
            Map<String, Object> envelope = Map.of(
                    "event", event.name(),
                    "timestamp", Instant.now().toString(),
                    "payload", entityPayload
            );
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao serializar payload do webhook", e);
        }
    }
}
