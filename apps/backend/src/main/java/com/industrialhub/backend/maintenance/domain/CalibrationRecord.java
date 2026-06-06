package com.industrialhub.backend.maintenance.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "calibration_record", indexes = {
    @Index(name = "idx_cr_schedule_id",   columnList = "schedule_id"),
    @Index(name = "idx_cr_equipment_id",  columnList = "equipment_id"),
    @Index(name = "idx_cr_calibrated_at", columnList = "calibrated_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalibrationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false, updatable = false)
    private CalibrationSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false, updatable = false)
    private Equipment equipment;

    @Column(nullable = false)
    private LocalDate calibratedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CalibrationResult result;

    @Column(nullable = false, length = 200)
    private String technician;

    /** UUID da revisão do documento no GED — mutuamente exclusivo com certificateStoragePath. */
    private UUID certificateDocumentId;

    /** Caminho no storage do certificado (upload direto) — mutuamente exclusivo com certificateDocumentId. */
    @Column(length = 500)
    private String certificateStoragePath;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** ID da NC gerada automaticamente quando result = OUT_OF_TOLERANCE. */
    private UUID autoNcId;

    @Column(nullable = false, updatable = false)
    private String recordedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    private void prePersist() {
        if (this.recordedAt == null) {
            this.recordedAt = LocalDateTime.now();
        }
    }
}
