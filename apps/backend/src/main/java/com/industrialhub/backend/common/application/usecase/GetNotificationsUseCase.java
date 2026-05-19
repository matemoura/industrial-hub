package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.NotificationResponse;
import com.industrialhub.backend.common.infrastructure.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetNotificationsUseCase {

    private final NotificationRepository notificationRepository;

    public GetNotificationsUseCase(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> execute(String username, Pageable pageable) {
        return notificationRepository
                .findByUsernameOrUsernameIsNullOrderByReadAtAscCreatedAtDesc(username, pageable)
                .map(NotificationResponse::from);
    }
}
