package com.industrialhub.backend.training.application.dto;

public record TrainingComplianceSummary(
    int totalUsers,
    int totalRequiredCompetencies,
    int valid,
    int expiring,
    int expired,
    int missing,
    double compliancePercent
) {}
