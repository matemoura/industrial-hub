package com.industrialhub.backend.common.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UserDataExportResponse(
    LocalDateTime exportedAt,
    ProfileSummary profile,
    List<NcSummaryForExport> nonConformancesReported,
    List<WorkOrderSummaryForExport> workOrdersOpened,
    List<AuditLogSummaryForExport> auditLogEntries
) {
    public record ProfileSummary(
        String username,
        String email,
        String role,
        boolean active
    ) {}

    public record NcSummaryForExport(
        UUID id,
        String title,
        String type,
        String severity,
        String status,
        LocalDateTime reportedAt
    ) {}

    public record WorkOrderSummaryForExport(
        UUID id,
        String title,
        String type,
        String priority,
        String status,
        LocalDateTime openedAt
    ) {}

    public record AuditLogSummaryForExport(
        UUID id,
        String action,
        String entityType,
        String entityId,
        LocalDateTime timestamp
    ) {}
}
