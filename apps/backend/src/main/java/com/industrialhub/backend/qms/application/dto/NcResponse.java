package com.industrialhub.backend.qms.application.dto;

import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.domain.NonConformance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record NcResponse(
    UUID id,
    String title,
    String description,
    NcType type,
    NcSeverity severity,
    NcStatus status,
    String reportedBy,
    LocalDateTime reportedAt,
    LocalDateTime closedAt,
    String closedBy,
    UUID supplierId,
    String supplierName,
    List<ActionResponse> actions,
    RcaResponse rca
) {
    public static NcResponse from(NonConformance nc) {
        return new NcResponse(
            nc.getId(),
            nc.getTitle(),
            nc.getDescription(),
            nc.getType(),
            nc.getSeverity(),
            nc.getStatus(),
            nc.getReportedBy(),
            nc.getReportedAt(),
            nc.getClosedAt(),
            nc.getClosedBy(),
            nc.getSupplier() != null ? nc.getSupplier().getId() : null,
            nc.getSupplier() != null ? nc.getSupplier().getName() : null,
            nc.getActions().stream().map(ActionResponse::from).toList(),
            nc.getRca() != null ? RcaResponse.from(nc.getRca()) : null
        );
    }
}
