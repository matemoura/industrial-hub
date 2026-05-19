package com.industrialhub.backend.common.application.dto;

import com.industrialhub.backend.common.domain.AlertMetric;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateAlertThresholdRequest(
        @NotNull(message = "metric é obrigatório")
        AlertMetric metric,

        @NotNull(message = "threshold é obrigatório")
        @Positive(message = "threshold deve ser positivo")
        Double threshold,

        boolean emailEnabled
) {}
