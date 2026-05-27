package com.industrialhub.backend.production.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_snapshot", indexes = {
        @Index(name = "idx_stock_product", columnList = "product_id"),
        @Index(name = "idx_stock_date", columnList = "snapshot_date")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer qty;

    private LocalDate snapshotDate;
    private LocalDateTime importedAt;

    @Column(length = 100)
    private String importedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    private ImportProductionBatch importBatch;
}
