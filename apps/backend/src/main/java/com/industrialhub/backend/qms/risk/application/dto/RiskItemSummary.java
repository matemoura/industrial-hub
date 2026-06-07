package com.industrialhub.backend.qms.risk.application.dto;

import com.industrialhub.backend.qms.risk.domain.RiskItem;
import com.industrialhub.backend.qms.risk.domain.RiskLevel;
import com.industrialhub.backend.qms.risk.domain.RiskStatus;

import java.util.UUID;

public record RiskItemSummary(
    UUID id,
    String process,
    Integer rpn,
    RiskLevel riskLevel,
    RiskStatus status
) {
    public static RiskItemSummary from(RiskItem item) {
        return new RiskItemSummary(
            item.getId(),
            item.getProcess(),
            item.getRpn(),
            item.getRiskLevel(),
            item.getStatus()
        );
    }
}
