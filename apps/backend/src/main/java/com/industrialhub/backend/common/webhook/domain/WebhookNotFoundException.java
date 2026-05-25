package com.industrialhub.backend.common.webhook.domain;

import java.util.UUID;

public class WebhookNotFoundException extends RuntimeException {

    public WebhookNotFoundException(UUID id) {
        super("Webhook não encontrado: " + id);
    }
}
