package com.industrialhub.backend.production.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** ADR-030 Decisão 2 — histórico de execuções do motor MRP */
@Entity
@Table(name = "mrp_run", indexes = {
        @Index(name = "idx_mrp_run_at", columnList = "run_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MrpRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_at", nullable = false)
    private LocalDateTime runAt;

    @Column(name = "run_by", length = 100)
    private String runBy;

    @Column(name = "is_dry_run", nullable = false)
    private boolean isDryRun;

    @Column(name = "stock_snapshot_date")
    private LocalDate stockSnapshotDate;

    @Column(name = "orders_snapshot_date")
    private LocalDate ordersSnapshotDate;

    @Column(name = "products_analyzed")
    private Integer productsAnalyzed;

    @Column(name = "suggestions_generated")
    private Integer suggestionsGenerated;

    @Column(name = "already_ok")
    private Integer alreadyOk;

    @Column(name = "purchase_needs_json", columnDefinition = "TEXT")
    private String purchaseNeedsJson;

    @Column(name = "messages_json", columnDefinition = "TEXT")
    private String messagesJson;
}
