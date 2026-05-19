package com.industrialhub.backend.common.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateAlertThresholdRequest(
        @NotNull(message = "threshold é obrigatório")
        @Positive(message = "threshold deve ser positivo")
        Double threshold,

        boolean emailEnabled
) {}
