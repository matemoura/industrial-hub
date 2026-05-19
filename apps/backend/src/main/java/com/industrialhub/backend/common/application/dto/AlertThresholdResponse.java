package com.industrialhub.backend.common.application.dto;

import com.industrialhub.backend.common.domain.AlertMetric;
import com.industrialhub.backend.common.domain.AlertThreshold;

import java.time.LocalDateTime;
import java.util.UUID;

public record AlertThresholdResponse(
        UUID id,
        AlertMetric metric,
        Double threshold,
        boolean emailEnabled,
        boolean active,
        LocalDateTime updatedAt
) {
    public static AlertThresholdResponse from(AlertThreshold t) {
        return new AlertThresholdResponse(
                t.getId(),
                t.getMetric(),
                t.getThreshold(),
                t.isEmailEnabled(),
                t.isActive(),
                t.getUpdatedAt()
        );
    }
}
