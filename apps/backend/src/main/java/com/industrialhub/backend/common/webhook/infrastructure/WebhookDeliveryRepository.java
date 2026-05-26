package com.industrialhub.backend.common.webhook.infrastructure;

import com.industrialhub.backend.common.webhook.domain.DeliveryStatus;
import com.industrialhub.backend.common.webhook.domain.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    @Query("SELECT d FROM WebhookDelivery d WHERE d.subscription.id = :subscriptionId ORDER BY d.createdAt DESC")
    List<WebhookDelivery> findTop50BySubscriptionId(@Param("subscriptionId") UUID subscriptionId,
                                                     org.springframework.data.domain.Pageable pageable);

    @Query("SELECT d FROM WebhookDelivery d WHERE d.subscription.id = :subscriptionId AND d.status = :status")
    List<WebhookDelivery> findBySubscriptionIdAndStatus(@Param("subscriptionId") UUID subscriptionId,
                                                         @Param("status") DeliveryStatus status);
}
