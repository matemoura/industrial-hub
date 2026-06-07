package com.industrialhub.backend.qms.audit.application.dto;

import com.industrialhub.backend.qms.audit.domain.AuditType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record CreateInternalAuditRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank String scope,
    @NotNull AuditType auditType,
    @NotNull LocalDate plannedDate,
    @NotBlank @Size(max = 100) String leadAuditor,
    Set<String> auditees
) {}
