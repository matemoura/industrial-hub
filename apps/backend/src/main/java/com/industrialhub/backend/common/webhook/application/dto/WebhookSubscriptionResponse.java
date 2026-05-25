package com.industrialhub.backend.common.webhook.application.dto;

import com.industrialhub.backend.common.webhook.domain.WebhookEvent;
import com.industrialhub.backend.common.webhook.domain.WebhookSubscription;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record WebhookSubscriptionResponse(
    UUID id,
    String url,
    boolean hasSecret,
    Set<WebhookEvent> events,
    boolean active,
    String description,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime disabledAt
) {
    public static WebhookSubscriptionResponse from(WebhookSubscription sub) {
        return new WebhookSubscriptionResponse(
            sub.getId(),
            sub.getUrl(),
            sub.getSecret() != null && !sub.getSecret().isBlank(),
            sub.getEvents(),
            sub.isActive(),
            sub.getDescription(),
            sub.getCreatedBy(),
            sub.getCreatedAt(),
            sub.getUpdatedAt(),
            sub.getDisabledAt()
        );
    }
}
