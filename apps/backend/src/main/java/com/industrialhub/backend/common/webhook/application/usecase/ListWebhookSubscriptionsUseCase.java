package com.industrialhub.backend.common.webhook.application.usecase;

import com.industrialhub.backend.common.webhook.application.dto.WebhookSubscriptionResponse;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListWebhookSubscriptionsUseCase {

    private final WebhookSubscriptionRepository repository;

    public ListWebhookSubscriptionsUseCase(WebhookSubscriptionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<WebhookSubscriptionResponse> execute() {
        return repository.findAll().stream()
                .map(WebhookSubscriptionResponse::from)
                .toList();
    }
}
