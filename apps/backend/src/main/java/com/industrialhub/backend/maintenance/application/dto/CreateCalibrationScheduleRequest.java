package com.industrialhub.backend.maintenance.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateCalibrationScheduleRequest(
    @NotNull UUID equipmentId,
    @NotNull @Min(1) Integer intervalDays,
    String externalProvider
) {}
