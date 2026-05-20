package com.industrialhub.backend.common.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record CreateShiftRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        boolean overnight
) {
}
