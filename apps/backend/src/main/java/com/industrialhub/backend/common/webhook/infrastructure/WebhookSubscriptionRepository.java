package com.industrialhub.backend.common.webhook.infrastructure;

import com.industrialhub.backend.common.webhook.domain.WebhookEvent;
import com.industrialhub.backend.common.webhook.domain.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {

    @Query("SELECT s FROM WebhookSubscription s WHERE s.active = :active AND :event MEMBER OF s.events")
    List<WebhookSubscription> findByActiveAndEventsContaining(@Param("active") boolean active,
                                                               @Param("event") WebhookEvent event);

    List<WebhookSubscription> findAll();
}
