package com.industrialhub.backend.training.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "training_record", indexes = {
    @Index(name = "idx_training_record_username",   columnList = "username"),
    @Index(name = "idx_training_record_course",     columnList = "course_id"),
    @Index(name = "idx_training_record_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private TrainingCourse course;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private LocalDate completedAt;

    private LocalDate expiresAt;

    @Column(length = 200)
    private String instructorName;

    @Min(0) @Max(100)
    private Integer score;

    @Column(nullable = false)
    private boolean passed;

    @Column(length = 500)
    private String certificateStoragePath;

    @Column(nullable = false, length = 100)
    private String recordedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    private LocalDate effectivenessAssessedAt;

    @Column(length = 100)
    private String effectivenessAssessedBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EffectivenessResult effectivenessResult;

    @Column(columnDefinition = "TEXT")
    private String effectivenessNotes;

    @PrePersist
    void prePersist() {
        if (recordedAt == null) recordedAt = LocalDateTime.now();
    }
}
