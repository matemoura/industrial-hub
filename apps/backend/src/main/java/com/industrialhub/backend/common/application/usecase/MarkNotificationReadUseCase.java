package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.NotificationResponse;
import com.industrialhub.backend.common.domain.Notification;
import com.industrialhub.backend.common.domain.NotificationNotFoundException;
import com.industrialhub.backend.common.infrastructure.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class MarkNotificationReadUseCase {

    private final NotificationRepository notificationRepository;

    public MarkNotificationReadUseCase(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public NotificationResponse execute(UUID id, String currentUsername) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));

        // Verificar ownership: broadcasts (username == null) podem ser lidos por qualquer um
        // Notificações pessoais (username != null) só pelo destinatário
        if (notification.getUsername() != null && !notification.getUsername().equals(currentUsername)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Você não tem permissão para marcar esta notificação como lida.");
        }

        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }

        return NotificationResponse.from(notification);
    }
}
