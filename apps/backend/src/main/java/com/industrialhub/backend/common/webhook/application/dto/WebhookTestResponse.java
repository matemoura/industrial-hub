package com.industrialhub.backend.common.webhook.application.dto;

public record WebhookTestResponse(
    String url,
    Integer responseCode,
    Long durationMs,
    boolean success,
    String errorMessage
) {}
