package com.industrialhub.backend.common.webhook.domain;

public class WebhookInvalidUrlException extends RuntimeException {
    public WebhookInvalidUrlException(String message) {
        super(message);
    }
}
