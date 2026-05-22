package com.industrialhub.backend.common.application.dto;

public record DataRetentionPreview(
    long usersEligible,
    long auditLogsEligible,
    long nonConformancesEligible,
    long workOrdersEligible,
    long notificationsEligible
) {}
