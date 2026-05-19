package com.industrialhub.backend.common.domain;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(UUID id) {
        super("Notificação não encontrada: " + id);
    }
}
