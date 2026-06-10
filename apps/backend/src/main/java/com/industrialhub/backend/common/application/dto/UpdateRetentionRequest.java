package com.industrialhub.backend.common.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateRetentionRequest(
        @NotNull @Min(30) @Max(3650) Integer retentionDays
) {}
