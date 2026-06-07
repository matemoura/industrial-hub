package com.industrialhub.backend.qms.audit.application.dto;

import com.industrialhub.backend.qms.audit.domain.ChecklistResponse;
import jakarta.validation.constraints.NotNull;

public record UpdateAuditChecklistItemRequest(
    @NotNull ChecklistResponse response,
    String evidence
) {}
