package com.industrialhub.backend.qms.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEffectivenessRequest(
    @NotBlank @Size(max = 2000) String effectivenessResult,
    @NotBlank @Size(max = 200) String effectivenessCheckedBy
) {}
