package com.industrialhub.backend.maintenance.application.dto;

public record CalibrationSummary(
    long totalSchedules,
    long overdueCount,
    long dueSoon14Days,
    long lastMonthRecords,
    long outOfToleranceLastMonth
) {}
