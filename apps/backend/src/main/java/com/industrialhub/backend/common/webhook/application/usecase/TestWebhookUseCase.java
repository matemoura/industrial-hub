package com.industrialhub.backend.common.webhook.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.webhook.application.dto.WebhookTestResponse;
import com.industrialhub.backend.common.webhook.domain.DeliveryStatus;
import com.industrialhub.backend.common.webhook.domain.WebhookDelivery;
import com.industrialhub.backend.common.webhook.domain.WebhookEvent;
import com.industrialhub.backend.common.webhook.domain.WebhookNotFoundException;
import com.industrialhub.backend.common.webhook.domain.WebhookSubscription;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookDeliveryRepository;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookSubscriptionRepository;
import com.industrialhub.backend.common.webhook.service.WebhookSignatureService;
import com.industrialhub.backend.common.webhook.service.WebhookUrlValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class TestWebhookUseCase {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSignatureService signatureService;
    private final RestTemplate webhookRestTemplate;
    private final WebhookUrlValidator webhookUrlValidator;
    private final AuditService auditService;

    public TestWebhookUseCase(WebhookSubscriptionRepository subscriptionRepository,
                               WebhookDeliveryRepository deliveryRepository,
                               WebhookSignatureService signatureService,
                               @Qualifier("webhookRestTemplate") RestTemplate webhookRestTemplate,
                               WebhookUrlValidator webhookUrlValidator,
                               AuditService auditService) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.signatureService = signatureService;
        this.webhookRestTemplate = webhookRestTemplate;
        this.webhookUrlValidator = webhookUrlValidator;
        this.auditService = auditService;
    }

    @Transactional
    public WebhookTestResponse execute(UUID id, String performedBy) {
        WebhookSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new WebhookNotFoundException(id));

        webhookUrlValidator.validate(subscription.getUrl());

        String testPayload = String.format(
                "{\"event\":\"TEST\",\"timestamp\":\"%s\",\"payload\":{\"message\":\"Webhook test from Industrial Hub\"}}",
                Instant.now());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (subscription.getSecret() != null && !subscription.getSecret().isBlank()) {
            headers.set("X-Hub-Signature-256", signatureService.sign(subscription.getSecret(), testPayload));
        }
        headers.set("X-Industrial-Hub-Event", "TEST");

        long start = System.currentTimeMillis();
        Integer responseCode = null;
        String errorMessage = null;
        boolean success = false;

        try {
            ResponseEntity<String> response = webhookRestTemplate.postForEntity(
                    subscription.getUrl(), new HttpEntity<>(testPayload, headers), String.class);
            long duration = System.currentTimeMillis() - start;
            responseCode = response.getStatusCode().value();
            success = response.getStatusCode().is2xxSuccessful();

            // Persist delivery record
            deliveryRepository.save(WebhookDelivery.builder()
                    .subscription(subscription)
                    .event(WebhookEvent.NC_CREATED) // placeholder for test
                    .attempt(1)
                    .responseCode(responseCode)
                    .durationMs(duration)
                    .status(success ? DeliveryStatus.SUCCESS : DeliveryStatus.FAILED)
                    .createdAt(LocalDateTime.now())
                    .build());

            auditService.log(performedBy, AuditAction.WEBHOOK_TESTED, "WebhookSubscription",
                    subscription.getId().toString(),
                    Map.of("event", "TEST", "responseCode", responseCode));

            return new WebhookTestResponse(subscription.getUrl(), responseCode, duration, success, null);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            errorMessage = e.getMessage();

            deliveryRepository.save(WebhookDelivery.builder()
                    .subscription(subscription)
                    .event(WebhookEvent.NC_CREATED)
                    .attempt(1)
                    .responseCode(null)
                    .durationMs(duration)
                    .status(DeliveryStatus.FAILED)
                    .errorMessage(errorMessage)
                    .createdAt(LocalDateTime.now())
                    .build());

            auditService.log(performedBy, AuditAction.WEBHOOK_TESTED, "WebhookSubscription",
                    subscription.getId().toString(),
                    Map.of("event", "TEST", "responseCode", "null", "error", errorMessage != null ? errorMessage : ""));

            return new WebhookTestResponse(subscription.getUrl(), null, duration, false, errorMessage);
        }
    }
}
