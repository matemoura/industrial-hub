package com.industrialhub.backend.qms.risk.application.dto;

import com.industrialhub.backend.qms.risk.domain.RiskLevel;

public record RiskMatrixCell(
    Integer severity,
    Integer occurrence,
    Long count,
    RiskLevel riskLevel
) {}
