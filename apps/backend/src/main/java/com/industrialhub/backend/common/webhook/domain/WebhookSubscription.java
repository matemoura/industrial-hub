package com.industrialhub.backend.common.webhook.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "webhook_subscription")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 100)
    private String secret;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "webhook_subscription_events",
        joinColumns = @JoinColumn(name = "webhook_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50)
    @Builder.Default
    private Set<WebhookEvent> events = new HashSet<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, length = 50)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime disabledAt;
}
