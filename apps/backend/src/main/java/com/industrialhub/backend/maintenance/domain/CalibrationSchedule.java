package com.industrialhub.backend.maintenance.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "calibration_schedule", indexes = {
    @Index(name = "idx_cs_equipment_id", columnList = "equipment_id"),
    @Index(name = "idx_cs_next_due_at",  columnList = "next_due_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalibrationSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false, updatable = false)
    private Equipment equipment;

    @Column(nullable = false)
    private Integer intervalDays;

    private LocalDate lastCalibratedAt;

    @Column(name = "next_due_at")
    private LocalDate nextDueAt;

    @Column(length = 200)
    private String externalProvider;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
