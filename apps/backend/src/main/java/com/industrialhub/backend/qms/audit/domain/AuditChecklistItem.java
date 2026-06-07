package com.industrialhub.backend.qms.audit.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "audit_checklist_item",
    indexes = {
        @Index(name = "idx_checklist_item_audit_id", columnList = "audit_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_id", nullable = false, updatable = false)
    private InternalAudit audit;

    @Column(nullable = false, length = 100)
    private String process;

    @Column(name = "iso_clause", nullable = false, length = 20)
    private String isoClause;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ChecklistResponse response;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "item_order")
    private Integer itemOrder;
}
