package com.industrialhub.backend.common.webhook.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_delivery",
    indexes = {
        @Index(name = "idx_webhook_delivery_subscription", columnList = "subscription_id"),
        @Index(name = "idx_webhook_delivery_created_at",   columnList = "created_at")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private WebhookSubscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private WebhookEvent event;

    @Column(nullable = false)
    private int attempt;

    @Column
    private Integer responseCode;

    @Column
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
