package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.SterilizationMethod;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateSterilizationLoadRequest(
        UUID sterilizerId,             // nullable
        SterilizationMethod method,
        LocalDate sterilizationDate,   // nullable
        @Size(max = 80) String batchCode,
        @Size(max = 500) String notes
) {}
