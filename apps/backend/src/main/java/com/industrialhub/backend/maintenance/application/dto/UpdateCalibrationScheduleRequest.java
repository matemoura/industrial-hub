package com.industrialhub.backend.maintenance.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCalibrationScheduleRequest(
    @NotNull @Min(1) Integer intervalDays,
    String externalProvider
) {}
