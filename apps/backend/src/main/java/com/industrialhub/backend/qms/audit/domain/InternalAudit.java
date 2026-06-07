package com.industrialhub.backend.qms.audit.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "internal_audit",
    indexes = {
        @Index(name = "idx_internal_audit_status", columnList = "status"),
        @Index(name = "idx_internal_audit_planned_date", columnList = "planned_date")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditType auditType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditStatus status;

    @Column(name = "planned_date", nullable = false)
    private LocalDate plannedDate;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Column(nullable = false, length = 100)
    private String leadAuditor;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "internal_audit_auditee",
        joinColumns = @JoinColumn(name = "audit_id"))
    @Column(name = "auditee")
    @Builder.Default
    private Set<String> auditees = new HashSet<>();

    @Column(nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = AuditStatus.PLANNED;
    }
}
