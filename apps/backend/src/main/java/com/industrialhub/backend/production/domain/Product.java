package com.industrialhub.backend.production.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "product", indexes = {
        @Index(name = "idx_product_code", columnList = "dynamics_code", unique = true),
        @Index(name = "idx_product_family", columnList = "family_id"),
        @Index(name = "idx_product_type", columnList = "type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dynamics_code", unique = true, nullable = false, length = 100)
    private String dynamicsCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private ProductFamily family;

    @Column(length = 20)
    private String unit;

    private Integer leadTimeDays;
    private Integer minStockQty;
    private Integer batchSize;

    @Builder.Default
    private boolean requiresSterilization = false;

    @Builder.Default
    private boolean active = true;

    private LocalDateTime lastSyncAt;
}
