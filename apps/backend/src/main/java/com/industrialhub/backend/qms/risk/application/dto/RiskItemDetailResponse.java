package com.industrialhub.backend.qms.risk.application.dto;

import com.industrialhub.backend.qms.risk.domain.RiskItem;
import com.industrialhub.backend.qms.risk.domain.RiskLevel;
import com.industrialhub.backend.qms.risk.domain.RiskStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RiskItemDetailResponse(
    UUID id,
    String process,
    String failureMode,
    String failureEffect,
    String failureCause,
    Integer severity,
    Integer occurrence,
    Integer detectability,
    Integer rpn,
    RiskLevel riskLevel,
    RiskStatus status,
    String owner,
    UUID linkedNcId,
    String linkedProductCode,
    LocalDateTime createdAt,
    List<MitigationActionResponse> mitigationActions
) {
    public static RiskItemDetailResponse from(RiskItem item, List<MitigationActionResponse> actions) {
        return new RiskItemDetailResponse(
            item.getId(),
            item.getProcess(),
            item.getFailureMode(),
            item.getFailureEffect(),
            item.getFailureCause(),
            item.getSeverity(),
            item.getOccurrence(),
            item.getDetectability(),
            item.getRpn(),
            item.getRiskLevel(),
            item.getStatus(),
            item.getOwner(),
            item.getLinkedNcId(),
            item.getLinkedProductCode(),
            item.getCreatedAt(),
            actions
        );
    }
}
