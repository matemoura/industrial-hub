package com.industrialhub.backend.production.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cycle_time",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cycle_time_product_date",
                columnNames = {"product_id", "effective_date"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CycleTime {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "seconds_per_unit", nullable = false)
    private Double secondsPerUnit;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(length = 100)
    private String importedBy;

    private LocalDateTime importedAt;
}
