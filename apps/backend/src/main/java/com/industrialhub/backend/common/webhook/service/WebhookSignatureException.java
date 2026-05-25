package com.industrialhub.backend.common.webhook.service;

public class WebhookSignatureException extends RuntimeException {

    public WebhookSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
