package com.industrialhub.backend.qms.audit.application.dto;

import com.industrialhub.backend.qms.audit.domain.AuditChecklistItem;
import com.industrialhub.backend.qms.audit.domain.ChecklistResponse;

import java.util.UUID;

public record AuditChecklistItemResponse(
    UUID id,
    UUID auditId,
    String process,
    String isoClause,
    String question,
    ChecklistResponse response,
    String evidence,
    Integer itemOrder
) {
    public static AuditChecklistItemResponse from(AuditChecklistItem item) {
        return new AuditChecklistItemResponse(
            item.getId(),
            item.getAudit().getId(),
            item.getProcess(),
            item.getIsoClause(),
            item.getQuestion(),
            item.getResponse(),
            item.getEvidence(),
            item.getItemOrder()
        );
    }
}
