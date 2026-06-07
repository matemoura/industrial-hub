package com.industrialhub.backend.qms.audit.application.dto;

import com.industrialhub.backend.qms.audit.domain.AuditFinding;
import com.industrialhub.backend.qms.audit.domain.FindingType;
import com.industrialhub.backend.qms.domain.NcSeverity;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditFindingResponse(
    UUID id,
    UUID auditId,
    FindingType type,
    String description,
    String isoClause,
    NcSeverity severity,
    UUID linkedNcId,
    UUID linkedCapaId,
    LocalDateTime createdAt
) {
    public static AuditFindingResponse from(AuditFinding f) {
        return new AuditFindingResponse(
            f.getId(),
            f.getAudit().getId(),
            f.getType(),
            f.getDescription(),
            f.getIsoClause(),
            f.getSeverity(),
            f.getLinkedNcId(),
            f.getLinkedCapaId(),
            f.getCreatedAt()
        );
    }
}
