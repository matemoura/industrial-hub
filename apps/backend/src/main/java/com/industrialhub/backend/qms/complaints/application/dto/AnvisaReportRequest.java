package com.industrialhub.backend.qms.complaints.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AnvisaReportRequest(
    @NotBlank String reportNumber,
    @NotNull LocalDate reportDate
) {}
