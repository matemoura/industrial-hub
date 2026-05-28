package com.industrialhub.backend.production.domain;

import com.industrialhub.backend.maintenance.domain.Equipment;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Formula;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sterilization_load", indexes = {
        @Index(name = "idx_load_status",      columnList = "status"),
        @Index(name = "idx_load_sterilizer",  columnList = "sterilizer_id"),
        @Index(name = "idx_load_date",        columnList = "sterilization_date")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SterilizationLoad {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String loadNumber;   // "CARGA-2026-001"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoadStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sterilizer_id")
    private Equipment sterilizer;   // nullable — módulo funciona sem esterilizador configurado

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SterilizationMethod method;

    private LocalDate sterilizationDate;

    @Column(length = 80)
    private String batchCode;   // código do lote ANVISA

    @Column(length = 500)
    private String notes;

    @Column(length = 100)
    private String createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
    private LocalDateTime releasedAt;

    /**
     * ADR-043 Decisão 5 — totalOrders via @Formula (subquery inline, sem N+1).
     * NOTA: SQL nativo — se a tabela production_order for renomeada, atualizar aqui.
     */
    @Formula("(SELECT COUNT(*) FROM production_order po WHERE po.sterilization_load_id = id)")
    private Integer totalOrders;
}
