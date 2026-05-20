package com.industrialhub.backend.maintenance.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "spare_part", indexes = {
    @Index(name = "idx_part_code",     columnList = "code", unique = true),
    @Index(name = "idx_part_category", columnList = "category")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SparePart {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(length = 50)
    private String unit;

    private Integer stockQty;

    private Integer minStockQty;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
