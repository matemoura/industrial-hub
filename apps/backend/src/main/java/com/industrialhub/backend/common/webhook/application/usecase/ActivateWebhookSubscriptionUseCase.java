package com.industrialhub.backend.common.webhook.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.webhook.application.dto.WebhookSubscriptionResponse;
import com.industrialhub.backend.common.webhook.domain.WebhookNotFoundException;
import com.industrialhub.backend.common.webhook.domain.WebhookSubscription;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class ActivateWebhookSubscriptionUseCase {

    private final WebhookSubscriptionRepository repository;
    private final AuditService auditService;

    public ActivateWebhookSubscriptionUseCase(WebhookSubscriptionRepository repository,
                                               AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public WebhookSubscriptionResponse execute(UUID id, String performedBy) {
        WebhookSubscription subscription = repository.findById(id)
                .orElseThrow(() -> new WebhookNotFoundException(id));

        subscription.setActive(true);
        subscription.setDisabledAt(null);
        subscription.setUpdatedAt(LocalDateTime.now());

        WebhookSubscription saved = repository.save(subscription);

        auditService.log(performedBy, AuditAction.WEBHOOK_ACTIVATED, "WebhookSubscription",
                saved.getId().toString(),
                Map.of("url", saved.getUrl()));

        return WebhookSubscriptionResponse.from(saved);
    }
}
