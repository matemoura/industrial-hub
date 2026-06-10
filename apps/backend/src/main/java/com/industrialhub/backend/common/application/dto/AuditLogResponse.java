package com.industrialhub.backend.common.application.dto;

import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.AuditLog;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    LocalDateTime timestamp,
    String username,
    AuditAction action,
    String entityType,
    String entityId,
    String module,
    String details,
    String beforeState,
    String afterState,
    String ipAddress
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
            log.getId(),
            log.getTimestamp(),
            log.getUsername(),
            log.getAction(),
            log.getEntityType(),
            log.getEntityId(),
            log.getModule(),
            log.getDetails(),
            log.getBeforeState(),
            log.getAfterState(),
            log.getIpAddress()
        );
    }
}
