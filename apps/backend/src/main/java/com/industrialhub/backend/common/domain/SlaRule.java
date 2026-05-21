package com.industrialhub.backend.common.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sla_rule",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_sla_rule_entity_classifier",
        columnNames = {"entity_type", "classifier_field", "classifier_value"}
    ),
    indexes = {
        @Index(name = "idx_sla_rule_entity_classifier",
               columnList = "entity_type, classifier_field, classifier_value")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SlaRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private SlaEntityType entityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "classifier_field", nullable = false, length = 20)
    private SlaClassifierField classifierField;

    @Column(name = "classifier_value", nullable = false, length = 30)
    private String classifierValue;

    @Column(name = "sla_hours", nullable = false)
    private int slaHours;

    @Column(name = "escalate_by_email", nullable = false)
    @Builder.Default
    private boolean escalateByEmail = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
