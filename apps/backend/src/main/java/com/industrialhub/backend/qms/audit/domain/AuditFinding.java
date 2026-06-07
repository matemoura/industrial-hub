package com.industrialhub.backend.qms.audit.domain;

import com.industrialhub.backend.qms.domain.NcSeverity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_finding",
    indexes = {
        @Index(name = "idx_audit_finding_audit_id", columnList = "audit_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_id", nullable = false, updatable = false)
    private InternalAudit audit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_item_id")
    private AuditChecklistItem checklistItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FindingType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "iso_clause", nullable = false, length = 20)
    private String isoClause;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NcSeverity severity;

    @Column(name = "linked_nc_id")
    private UUID linkedNcId;

    @Column(name = "linked_capa_id")
    private UUID linkedCapaId;

    @Column(nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
