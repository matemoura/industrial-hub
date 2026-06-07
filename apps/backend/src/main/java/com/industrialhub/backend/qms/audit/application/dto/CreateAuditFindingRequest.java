package com.industrialhub.backend.qms.audit.application.dto;

import com.industrialhub.backend.qms.audit.domain.FindingType;
import com.industrialhub.backend.qms.domain.NcSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateAuditFindingRequest(
    @NotNull FindingType type,
    @NotBlank String description,
    @NotBlank @Size(max = 20) String isoClause,
    @NotNull NcSeverity severity,
    UUID checklistItemId,
    UUID linkedNcId,
    UUID linkedCapaId
) {}
