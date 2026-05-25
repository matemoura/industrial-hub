package com.industrialhub.backend.common.webhook.application.dto;

import com.industrialhub.backend.common.webhook.domain.DeliveryStatus;
import com.industrialhub.backend.common.webhook.domain.WebhookDelivery;
import com.industrialhub.backend.common.webhook.domain.WebhookEvent;

import java.time.LocalDateTime;
import java.util.UUID;

public record WebhookDeliveryResponse(
    UUID id,
    WebhookEvent event,
    int attempt,
    Integer responseCode,
    Long durationMs,
    DeliveryStatus status,
    String errorMessage,
    LocalDateTime createdAt,
    boolean success
) {
    public static WebhookDeliveryResponse from(WebhookDelivery delivery) {
        Integer code = delivery.getResponseCode();
        boolean success = code != null && code >= 200 && code < 300;
        return new WebhookDeliveryResponse(
            delivery.getId(),
            delivery.getEvent(),
            delivery.getAttempt(),
            code,
            delivery.getDurationMs(),
            delivery.getStatus(),
            delivery.getErrorMessage(),
            delivery.getCreatedAt(),
            success
        );
    }
}
