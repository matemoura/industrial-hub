package com.industrialhub.backend.common.application.dto;

import java.time.LocalDateTime;

public record DataRetentionReport(
    int anonymizedUsers,
    int anonymizedAuditLogs,
    int anonymizedNonConformances,
    int anonymizedWorkOrders,
    int deletedNotifications,
    LocalDateTime executedAt
) {}
