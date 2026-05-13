package com.industrialhub.backend.qms.application.dto;

import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.domain.NonConformance;

import java.time.LocalDateTime;
import java.util.UUID;

public record NcSummaryItem(
    UUID id,
    String title,
    NcType type,
    NcSeverity severity,
    NcStatus status,
    String reportedBy,
    LocalDateTime reportedAt
) {
    public static NcSummaryItem from(NonConformance nc) {
        return new NcSummaryItem(
            nc.getId(),
            nc.getTitle(),
            nc.getType(),
            nc.getSeverity(),
            nc.getStatus(),
            nc.getReportedBy(),
            nc.getReportedAt()
        );
    }
}
