package com.industrialhub.backend.qms.risk.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CreateMitigationActionRequest(
    @NotBlank String description,
    @NotBlank String responsible,
    LocalDate targetDate
) {}
