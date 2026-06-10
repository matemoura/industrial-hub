package com.industrialhub.backend.common.application.dto;

import com.industrialhub.backend.common.domain.AuditRetentionConfig;

import java.time.LocalDateTime;

public record AuditRetentionConfigResponse(
        int retentionDays,
        LocalDateTime updatedAt,
        String updatedBy
) {
    public static AuditRetentionConfigResponse from(AuditRetentionConfig config) {
        return new AuditRetentionConfigResponse(
                config.getRetentionDays(), config.getUpdatedAt(), config.getUpdatedBy());
    }

    public static AuditRetentionConfigResponse defaultConfig() {
        return new AuditRetentionConfigResponse(365, null, null);
    }
}
