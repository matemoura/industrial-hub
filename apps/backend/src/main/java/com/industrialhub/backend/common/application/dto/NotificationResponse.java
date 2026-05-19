package com.industrialhub.backend.common.application.dto;

import com.industrialhub.backend.common.domain.Notification;
import com.industrialhub.backend.common.domain.NotificationSeverity;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String username,
        String title,
        String body,
        NotificationSeverity severity,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getUsername(),
                n.getTitle(),
                n.getBody(),
                n.getSeverity(),
                n.getCreatedAt(),
                n.getReadAt()
        );
    }
}
