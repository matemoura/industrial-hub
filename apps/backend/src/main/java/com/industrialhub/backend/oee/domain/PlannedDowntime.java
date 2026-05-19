package com.industrialhub.backend.oee.domain;

import com.industrialhub.backend.maintenance.domain.Equipment;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "planned_downtime", indexes = {
        @Index(name = "idx_pdt_equipment", columnList = "equipment_id"),
        @Index(name = "idx_pdt_start_at",  columnList = "start_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlannedDowntime {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id")
    private Equipment equipment;  // null = parada de planta inteira

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DowntimeReason reason;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private String registeredBy;

    @Column(nullable = false)
    private LocalDateTime registeredAt;

    /** Duração em minutos calculada a partir de startAt/endAt. */
    @Transient
    public int getDurationMinutes() {
        if (startAt == null || endAt == null) return 0;
        return (int) ChronoUnit.MINUTES.between(startAt, endAt);
    }

    /** Data (dia) da parada, derivada de startAt — mantém compatibilidade com cálculos OEE. */
    @Transient
    public LocalDate getDate() {
        return startAt != null ? startAt.toLocalDate() : null;
    }
}
