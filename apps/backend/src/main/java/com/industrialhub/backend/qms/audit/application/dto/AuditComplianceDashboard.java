package com.industrialhub.backend.qms.audit.application.dto;

import com.industrialhub.backend.qms.audit.domain.FindingType;

import java.util.Map;

public record AuditComplianceDashboard(
    long plannedThisYear,
    long completedThisYear,
    long overdueAudits,
    long openFindings,
    Map<FindingType, Long> findingsByType,
    double conformityRate
) {}
