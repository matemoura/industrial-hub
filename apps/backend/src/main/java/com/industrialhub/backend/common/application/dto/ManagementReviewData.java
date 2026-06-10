package com.industrialhub.backend.common.application.dto;

import java.util.Map;

public record ManagementReviewData(
    NcSummary ncSummary,
    CapaSummary capaSummary,
    ComplaintSummary complaintSummary,
    AuditSummary auditSummary,
    CalibrationSummary calibrationSummary,
    TrainingSummary trainingSummary,
    RiskSummary riskSummary,
    ChangeSummary changeSummary,
    KpiSnapshot kpiSummary
) {
    public record NcSummary(
        int totalReported,
        int criticalOpen,
        Double avgResolutionDays,
        Map<String, Integer> byStatus,
        Map<String, Integer> bySeverity
    ) {}

    public record CapaSummary(
        int totalOpen,
        int overdueCount,
        Double effectivenessRate
    ) {}

    public record ComplaintSummary(
        int totalReceived,
        int reportedToAnvisa,
        Double avgResolutionDays
    ) {}

    public record AuditSummary(
        int completed,
        int plannedNotDone,
        int overdueAudits,
        int nonConformingFindings,
        Double conformityRate
    ) {}

    public record CalibrationSummary(
        int overdueSchedules,
        int outOfToleranceCount,
        Double complianceRate
    ) {}

    public record TrainingSummary(
        int fullyCompliant,
        int partiallyCompliant,
        int nonCompliant,
        int expiringIn30Days
    ) {}

    public record RiskSummary(
        int totalRisks,
        int criticalOpen,
        int mitigatedInPeriod,
        Double avgRpn
    ) {}

    public record ChangeSummary(
        int submitted,
        int approved,
        int rejected,
        int implemented,
        int pending
    ) {}

    public record KpiSnapshot(
        Double oee30Days,
        int openNcs,
        int openWorkOrders
    ) {}
}
