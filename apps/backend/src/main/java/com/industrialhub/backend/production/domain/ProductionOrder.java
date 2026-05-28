package com.industrialhub.backend.production.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "production_order", indexes = {
        @Index(name = "idx_po_dynamics_number", columnList = "dynamics_order_number", unique = true),
        @Index(name = "idx_po_product", columnList = "product_id"),
        @Index(name = "idx_po_family", columnList = "family_id"),
        @Index(name = "idx_po_status", columnList = "status"),
        @Index(name = "idx_po_due_date", columnList = "due_date")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dynamics_order_number", unique = true, nullable = false, length = 50)
    private String dynamicsOrderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private ProductFamily family;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductionOrderStatus status;

    private BigDecimal plannedQty;
    private BigDecimal producedQty;

    private LocalDate startDate;
    private LocalDate dueDate;

    private LocalDateTime importedAt;
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    private ImportProductionBatch importBatch;

    // Hub-managed fields — preserved on upsert, never overwritten by Dynamics import
    private Integer plannedPeople;

    @Builder.Default
    private boolean peopleOverridden = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sterilization_load_id")
    private SterilizationLoad sterilizationLoad;  // nullable — set when OP is allocated to a load

    @PreUpdate
    public void onPreUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
