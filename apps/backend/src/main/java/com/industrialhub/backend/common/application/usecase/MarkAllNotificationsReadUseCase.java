package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.infrastructure.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class MarkAllNotificationsReadUseCase {

    private final NotificationRepository notificationRepository;

    public MarkAllNotificationsReadUseCase(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void execute(String username) {
        notificationRepository.markAllReadForUser(username, LocalDateTime.now());
    }
}
