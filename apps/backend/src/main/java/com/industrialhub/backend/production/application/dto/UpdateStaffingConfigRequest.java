package com.industrialhub.backend.production.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateStaffingConfigRequest(
        @NotNull @Min(1) @Max(24) Integer shiftHours,
        @NotNull @Min(1) @Max(3)  Integer shiftsPerDay
) {}
