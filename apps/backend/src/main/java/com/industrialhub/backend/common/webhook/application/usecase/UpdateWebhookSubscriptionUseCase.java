package com.industrialhub.backend.common.webhook.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.webhook.application.dto.UpdateWebhookRequest;
import com.industrialhub.backend.common.webhook.application.dto.WebhookSubscriptionResponse;
import com.industrialhub.backend.common.webhook.domain.WebhookNotFoundException;
import com.industrialhub.backend.common.webhook.domain.WebhookSubscription;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookSubscriptionRepository;
import com.industrialhub.backend.common.webhook.service.WebhookUrlValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UpdateWebhookSubscriptionUseCase {

    private final WebhookSubscriptionRepository repository;
    private final WebhookUrlValidator webhookUrlValidator;
    private final AuditService auditService;

    public UpdateWebhookSubscriptionUseCase(WebhookSubscriptionRepository repository,
                                             WebhookUrlValidator webhookUrlValidator,
                                             AuditService auditService) {
        this.repository = repository;
        this.webhookUrlValidator = webhookUrlValidator;
        this.auditService = auditService;
    }

    @Transactional
    public WebhookSubscriptionResponse execute(UUID id, UpdateWebhookRequest request, String performedBy) {
        WebhookSubscription subscription = repository.findById(id)
                .orElseThrow(() -> new WebhookNotFoundException(id));

        if (request.url() != null) {
            webhookUrlValidator.validate(request.url());
            subscription.setUrl(request.url());
        }
        if (request.secret() != null) {
            subscription.setSecret(request.secret().isBlank() ? null : request.secret());
        }
        if (request.events() != null && !request.events().isEmpty()) {
            subscription.setEvents(request.events());
        }
        if (request.description() != null) {
            subscription.setDescription(request.description());
        }
        subscription.setUpdatedAt(LocalDateTime.now());

        WebhookSubscription saved = repository.save(subscription);

        auditService.log(performedBy, AuditAction.WEBHOOK_UPDATED, "WebhookSubscription",
                saved.getId().toString(),
                Map.of("url", saved.getUrl(),
                        "events", saved.getEvents().stream()
                                .map(Enum::name)
                                .collect(Collectors.joining(","))));

        return WebhookSubscriptionResponse.from(saved);
    }
}
