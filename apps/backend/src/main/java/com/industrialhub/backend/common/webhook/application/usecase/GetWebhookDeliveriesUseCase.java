package com.industrialhub.backend.common.webhook.application.usecase;

import com.industrialhub.backend.common.webhook.application.dto.WebhookDeliveryResponse;
import com.industrialhub.backend.common.webhook.domain.WebhookNotFoundException;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookDeliveryRepository;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookSubscriptionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GetWebhookDeliveriesUseCase {

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;

    public GetWebhookDeliveriesUseCase(WebhookDeliveryRepository deliveryRepository,
                                        WebhookSubscriptionRepository subscriptionRepository) {
        this.deliveryRepository = deliveryRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryResponse> execute(UUID subscriptionId) {
        if (!subscriptionRepository.existsById(subscriptionId)) {
            throw new WebhookNotFoundException(subscriptionId);
        }
        return deliveryRepository.findTop50BySubscriptionId(subscriptionId, PageRequest.of(0, 50))
                .stream()
                .map(WebhookDeliveryResponse::from)
                .toList();
    }
}
