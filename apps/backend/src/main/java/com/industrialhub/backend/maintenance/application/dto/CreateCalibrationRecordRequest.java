package com.industrialhub.backend.maintenance.application.dto;

import com.industrialhub.backend.maintenance.domain.CalibrationResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateCalibrationRecordRequest(
    @NotNull UUID scheduleId,
    @NotNull LocalDate calibratedAt,
    @NotNull CalibrationResult result,
    @NotBlank @Size(max = 200) String technician,
    String notes,
    UUID certificateDocumentId
) {}
