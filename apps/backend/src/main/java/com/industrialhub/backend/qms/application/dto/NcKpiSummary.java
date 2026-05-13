package com.industrialhub.backend.qms.application.dto;

public record NcKpiSummary(
    long totalOpen,
    long totalInAnalysis,
    long totalClosed,
    long totalCritical,
    long totalThisMonth
) {}
