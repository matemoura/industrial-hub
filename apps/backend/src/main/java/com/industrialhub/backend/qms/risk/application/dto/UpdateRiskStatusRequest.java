package com.industrialhub.backend.qms.risk.application.dto;

import com.industrialhub.backend.qms.risk.domain.RiskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateRiskStatusRequest(
    @NotNull RiskStatus status
) {}
