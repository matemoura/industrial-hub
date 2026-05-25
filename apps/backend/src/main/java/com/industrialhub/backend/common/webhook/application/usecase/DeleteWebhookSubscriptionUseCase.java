package com.industrialhub.backend.common.webhook.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.webhook.domain.WebhookNotFoundException;
import com.industrialhub.backend.common.webhook.domain.WebhookSubscription;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class DeleteWebhookSubscriptionUseCase {

    private final WebhookSubscriptionRepository repository;
    private final AuditService auditService;

    public DeleteWebhookSubscriptionUseCase(WebhookSubscriptionRepository repository,
                                             AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public void execute(UUID id, String performedBy) {
        WebhookSubscription subscription = repository.findById(id)
                .orElseThrow(() -> new WebhookNotFoundException(id));

        String url = subscription.getUrl();
        repository.deleteById(id);

        auditService.log(performedBy, AuditAction.WEBHOOK_DELETED, "WebhookSubscription",
                id.toString(),
                Map.of("url", url));
    }
}
