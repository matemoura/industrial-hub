package com.industrialhub.backend.qms.risk.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "risk_mitigation_action",
    indexes = {
        @Index(name = "idx_risk_mitigation_risk_item_id", columnList = "risk_item_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskMitigationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "risk_item_id", nullable = false)
    private RiskItem riskItem;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(nullable = false, length = 100)
    private String responsible;

    @Column
    private LocalDate targetDate;

    @Column
    private LocalDate completedAt;

    @Column
    private Integer residualSeverity;

    @Column
    private Integer residualOccurrence;

    @Column
    private Integer residualDetectability;

    @Column
    private Integer residualRpn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MitigationStatus status;

    @Column(nullable = false, updatable = false, length = 100)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = MitigationStatus.PLANNED;
    }
}
