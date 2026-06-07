package com.industrialhub.backend.qms.risk.application.dto;

import com.industrialhub.backend.qms.risk.domain.RiskStatus;

import java.util.List;
import java.util.Map;

public record RiskSummary(
    long totalRisks,
    long criticalCount,
    long highCount,
    long mediumCount,
    long lowCount,
    Map<RiskStatus, Integer> byStatus,
    Double avgRpn,
    List<RiskItemSummary> topRisks
) {}
