package com.industrialhub.backend.common.application.dto;

import com.industrialhub.backend.common.domain.SlaClassifierField;
import com.industrialhub.backend.common.domain.SlaEntityType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSlaRuleRequest(
    @NotNull SlaEntityType entityType,
    @NotNull SlaClassifierField classifierField,
    @NotBlank @Size(max = 30) String classifierValue,
    @NotNull @Min(1) @Max(8760) Integer slaHours,
    boolean escalateByEmail
) {}
