package com.industrialhub.backend.qms.risk.application.dto;

import com.industrialhub.backend.qms.risk.domain.MitigationStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UpdateMitigationActionRequest(
    @NotBlank String description,
    @NotBlank String responsible,
    LocalDate targetDate,
    LocalDate completedAt,
    @Min(1) @Max(10) Integer residualSeverity,
    @Min(1) @Max(10) Integer residualOccurrence,
    @Min(1) @Max(10) Integer residualDetectability,
    @NotNull MitigationStatus status
) {}
