package com.industrialhub.backend.qms.audit.application.dto;

import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.AuditType;
import com.industrialhub.backend.qms.audit.domain.InternalAudit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record InternalAuditResponse(
    UUID id,
    String code,
    String title,
    String scope,
    AuditType auditType,
    AuditStatus status,
    LocalDate plannedDate,
    LocalDate completedDate,
    String leadAuditor,
    Set<String> auditees,
    long checklistItemsCount,
    long findingsCount,
    long nonConformingItemsCount,
    LocalDateTime createdAt
) {
    public static InternalAuditResponse from(InternalAudit a,
                                              long checklistItemsCount,
                                              long findingsCount,
                                              long nonConformingItemsCount) {
        return new InternalAuditResponse(
            a.getId(), a.getCode(), a.getTitle(), a.getScope(),
            a.getAuditType(), a.getStatus(), a.getPlannedDate(), a.getCompletedDate(),
            a.getLeadAuditor(), a.getAuditees(),
            checklistItemsCount, findingsCount, nonConformingItemsCount,
            a.getCreatedAt()
        );
    }
}
