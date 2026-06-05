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

    /** Sends a personal notification to a specific user (not a broadcast). */
    public void createForUser(String username, String title, String body,
                              NotificationSeverity severity) {
        Notification notification = Notification.builder()
                .username(username)
                .title(title)
                .body(body)
                .severity(severity)
                .createdAt(LocalDateTime.now())
                .build();
        notificationRepository.save(notification);
    }

    /**
     * Sends a personal notification with debounce — skips if the same title was
     * already sent to this user within the given debounce window.
     *
     * @param debounceHours hours to suppress duplicate notifications
     * @return 1 if notification was sent, 0 if suppressed by debounce
     */
    public int createForUserWithDebounce(String username, String title, String body,
                                          NotificationSeverity severity, int debounceHours) {
        LocalDateTime since = LocalDateTime.now().minusHours(debounceHours);
        if (notificationRepository.existsByTitleAndCreatedAtAfter(title, since)) {
            return 0;
        }
        createForUser(username, title, body, severity);
        return 1;
    }
}
