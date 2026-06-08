package com.industrialhub.backend.common.changes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "change_request",
    indexes = {
        @Index(name = "idx_change_request_status", columnList = "status"),
        @Index(name = "idx_change_request_change_type", columnList = "change_type"),
        @Index(name = "idx_change_request_requested_by", columnList = "requested_by"),
        @Index(name = "idx_change_request_code", columnList = "code")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private ChangeType changeType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String justification;

    @Column(name = "impact_assessment", columnDefinition = "TEXT")
    private String impactAssessment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeStatus status;

    @Column(name = "requested_by", nullable = false, length = 100, updatable = false)
    private String requestedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "implemented_at")
    private LocalDateTime implementedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = ChangeStatus.DRAFT;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
