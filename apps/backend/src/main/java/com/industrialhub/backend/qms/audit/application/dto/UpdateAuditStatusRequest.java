package com.industrialhub.backend.qms.audit.application.dto;

import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UpdateAuditStatusRequest(
    @NotNull AuditStatus status,
    LocalDate completedDate
) {}
