package com.industrialhub.backend.common.application;

import com.industrialhub.backend.common.domain.Notification;
import com.industrialhub.backend.common.domain.NotificationSeverity;
import com.industrialhub.backend.common.infrastructure.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void broadcast(String title, String body, NotificationSeverity severity) {
        Notification notification = Notification.builder()
                .username(null) // broadcast = null username
                .title(title)
                .body(body)
                .severity(severity)
                .createdAt(LocalDateTime.now())
                .build();
        notificationRepository.save(notification);
    }
}
