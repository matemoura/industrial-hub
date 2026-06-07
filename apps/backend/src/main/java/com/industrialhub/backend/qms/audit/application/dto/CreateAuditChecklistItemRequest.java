package com.industrialhub.backend.qms.audit.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAuditChecklistItemRequest(
    @NotBlank @Size(max = 100) String process,
    @NotBlank @Size(max = 20) String isoClause,
    @NotBlank String question
) {}
