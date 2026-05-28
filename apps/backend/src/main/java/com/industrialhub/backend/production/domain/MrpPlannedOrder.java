package com.industrialhub.backend.production.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** ADR-030 Decisão 1 — sugestão de OP gerada pelo motor MRP do Hub */
@Entity
@Table(name = "mrp_planned_order", indexes = {
        @Index(name = "idx_mrp_product",  columnList = "product_id"),
        @Index(name = "idx_mrp_family",   columnList = "family_id"),
        @Index(name = "idx_mrp_status",   columnList = "status"),
        @Index(name = "idx_mrp_run",      columnList = "mrp_run_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MrpPlannedOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private ProductFamily family;

    @Column(name = "suggested_qty", nullable = false)
    private Integer suggestedQty;

    @Column(name = "adjusted_qty")
    private Integer adjustedQty;

    @Column(name = "suggested_start_date")
    private LocalDate suggestedStartDate;

    @Column(name = "suggested_due_date")
    private LocalDate suggestedDueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MrpOrderStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mrp_run_id")
    private MrpRun mrpRun;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
