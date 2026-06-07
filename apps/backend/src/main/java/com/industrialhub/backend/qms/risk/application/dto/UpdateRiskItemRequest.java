package com.industrialhub.backend.qms.risk.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateRiskItemRequest(
    @NotBlank String process,
    @NotBlank String failureMode,
    @NotBlank String failureEffect,
    @NotBlank String failureCause,
    @NotNull @Min(1) @Max(10) Integer severity,
    @NotNull @Min(1) @Max(10) Integer occurrence,
    @NotNull @Min(1) @Max(10) Integer detectability,
    @NotBlank String owner,
    UUID linkedNcId,
    String linkedProductCode
) {}
