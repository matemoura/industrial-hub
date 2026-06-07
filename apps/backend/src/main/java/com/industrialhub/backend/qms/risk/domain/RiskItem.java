package com.industrialhub.backend.qms.risk.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "risk_item",
    indexes = {
        @Index(name = "idx_risk_item_status", columnList = "status"),
        @Index(name = "idx_risk_item_risk_level", columnList = "riskLevel"),
        @Index(name = "idx_risk_item_linked_nc_id", columnList = "linkedNcId")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String process;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String failureMode;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String failureEffect;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String failureCause;

    @Column(nullable = false)
    private Integer severity;

    @Column(nullable = false)
    private Integer occurrence;

    @Column(nullable = false)
    private Integer detectability;

    @Column(nullable = false)
    private Integer rpn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskStatus status;

    @Column(nullable = false, length = 100)
    private String owner;

    @Column
    private UUID linkedNcId;

    @Column(length = 100)
    private String linkedProductCode;

    @Column(nullable = false, updatable = false, length = 100)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = RiskStatus.IDENTIFIED;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void recalculateRpn() {
        this.rpn = severity * occurrence * detectability;
        this.riskLevel = RiskLevel.fromRpn(this.rpn);
    }
}
