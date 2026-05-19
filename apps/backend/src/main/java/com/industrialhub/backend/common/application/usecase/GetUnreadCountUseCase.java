package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.infrastructure.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetUnreadCountUseCase {

    private final NotificationRepository notificationRepository;

    public GetUnreadCountUseCase(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public long execute(String username) {
        return notificationRepository.countByUsernameOrUsernameIsNullAndReadAtIsNull(username);
    }
}
