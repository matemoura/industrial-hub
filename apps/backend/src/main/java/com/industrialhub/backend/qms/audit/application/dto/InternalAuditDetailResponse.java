package com.industrialhub.backend.qms.audit.application.dto;

import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.AuditType;
import com.industrialhub.backend.qms.audit.domain.ChecklistResponse;
import com.industrialhub.backend.qms.audit.domain.InternalAudit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record InternalAuditDetailResponse(
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
    LocalDateTime createdAt,
    List<AuditChecklistItemResponse> checklistItems,
    List<AuditFindingResponse> findings
) {
    public static InternalAuditDetailResponse from(InternalAudit a,
                                                    List<AuditChecklistItemResponse> items,
                                                    List<AuditFindingResponse> findings) {
        long ncCount = items.stream()
            .filter(i -> i.response() == ChecklistResponse.NON_CONFORMING)
            .count();
        return new InternalAuditDetailResponse(
            a.getId(), a.getCode(), a.getTitle(), a.getScope(),
            a.getAuditType(), a.getStatus(), a.getPlannedDate(), a.getCompletedDate(),
            a.getLeadAuditor(), a.getAuditees(),
            items.size(), findings.size(), ncCount,
            a.getCreatedAt(), items, findings
        );
    }
}
