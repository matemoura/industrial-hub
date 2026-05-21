package com.industrialhub.backend.common.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateSlaRuleRequest(
    @NotNull @Min(1) @Max(8760) Integer slaHours,
    boolean escalateByEmail
) {}
