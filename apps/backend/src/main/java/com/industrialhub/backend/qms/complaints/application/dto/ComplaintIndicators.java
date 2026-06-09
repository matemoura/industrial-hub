package com.industrialhub.backend.qms.complaints.application.dto;

import com.industrialhub.backend.qms.complaints.domain.ComplaintSource;
import com.industrialhub.backend.qms.complaints.domain.ComplaintStatus;
import com.industrialhub.backend.qms.domain.NcSeverity;

import java.util.List;
import java.util.Map;

public record ComplaintIndicators(
    int totalReceived,
    Map<ComplaintStatus, Integer> byStatus,
    Map<NcSeverity, Integer> bySeverity,
    int reportedToAnvisa,
    Double avgResolutionDays,
    List<ProductCount> byProduct,
    Map<ComplaintSource, Integer> bySource
) {
    public record ProductCount(String productCode, int count) {}
}
