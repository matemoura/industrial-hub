package com.industrialhub.backend.production.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * ADR-044 Decisão 1 — BOM normalizado (tabela de adjacência).
 * Representa um componente na estrutura de produto (BOM) importada do Dynamics.
 */
@Entity
@Table(
    name = "product_component",
    indexes = {
        @Index(name = "idx_bom_parent",    columnList = "parent_product_id"),
        @Index(name = "idx_bom_component", columnList = "component_product_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_bom_parent_component",
            columnNames = {"parent_product_id", "component_product_id"}
        )
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_product_id", nullable = false)
    private Product parentProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_product_id", nullable = false)
    private Product componentProduct;

    /** Unidades do componente por unidade do produto pai. */
    @Column(nullable = false)
    private Double quantity;

    /** Unidade de medida conforme Dynamics (ex: UN, KG, M). */
    @Column(length = 10)
    private String unit;

    /** Nível de BOM: 1 = componente direto do produto pai, 2 = sub-componente. */
    @Column(nullable = false)
    private Integer level;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;
}
