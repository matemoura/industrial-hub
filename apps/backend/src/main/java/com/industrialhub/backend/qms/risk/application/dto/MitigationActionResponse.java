package com.industrialhub.backend.qms.risk.application.dto;

import com.industrialhub.backend.qms.risk.domain.MitigationStatus;
import com.industrialhub.backend.qms.risk.domain.RiskMitigationAction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record MitigationActionResponse(
    UUID id,
    UUID riskItemId,
    String description,
    String responsible,
    LocalDate targetDate,
    LocalDate completedAt,
    Integer residualSeverity,
    Integer residualOccurrence,
    Integer residualDetectability,
    Integer residualRpn,
    MitigationStatus status,
    LocalDateTime createdAt
) {
    public static MitigationActionResponse from(RiskMitigationAction a) {
        return new MitigationActionResponse(
            a.getId(),
            a.getRiskItem().getId(),
            a.getDescription(),
            a.getResponsible(),
            a.getTargetDate(),
            a.getCompletedAt(),
            a.getResidualSeverity(),
            a.getResidualOccurrence(),
            a.getResidualDetectability(),
            a.getResidualRpn(),
            a.getStatus(),
            a.getCreatedAt()
        );
    }
}
