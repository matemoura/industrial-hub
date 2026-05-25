package com.industrialhub.backend.common.webhook.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.webhook.application.dto.CreateWebhookRequest;
import com.industrialhub.backend.common.webhook.application.dto.WebhookSubscriptionResponse;
import com.industrialhub.backend.common.webhook.domain.WebhookSubscription;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookSubscriptionRepository;
import com.industrialhub.backend.common.webhook.service.WebhookUrlValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CreateWebhookSubscriptionUseCase {

    private final WebhookSubscriptionRepository repository;
    private final WebhookUrlValidator webhookUrlValidator;
    private final AuditService auditService;

    public CreateWebhookSubscriptionUseCase(WebhookSubscriptionRepository repository,
                                             WebhookUrlValidator webhookUrlValidator,
                                             AuditService auditService) {
        this.repository = repository;
        this.webhookUrlValidator = webhookUrlValidator;
        this.auditService = auditService;
    }

    @Transactional
    public WebhookSubscriptionResponse execute(CreateWebhookRequest request, String performedBy) {
        webhookUrlValidator.validate(request.url());

        WebhookSubscription subscription = WebhookSubscription.builder()
                .url(request.url())
                .secret(request.secret())
                .events(request.events())
                .description(request.description())
                .active(true)
                .createdBy(performedBy)
                .createdAt(LocalDateTime.now())
                .build();

        WebhookSubscription saved = repository.save(subscription);

        auditService.log(performedBy, AuditAction.WEBHOOK_CREATED, "WebhookSubscription",
                saved.getId().toString(),
                Map.of("url", saved.getUrl(),
                        "events", saved.getEvents().stream()
                                .map(Enum::name)
                                .collect(Collectors.joining(","))));

        return WebhookSubscriptionResponse.from(saved);
    }
}
