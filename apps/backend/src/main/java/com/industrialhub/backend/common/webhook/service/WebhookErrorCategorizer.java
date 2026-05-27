package com.industrialhub.backend.common.webhook.service;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * Shared utility for categorizing webhook-related exceptions into safe, generic messages.
 * Prevents internal IPs, ports, and stack traces from leaking into responses and audit logs.
 */
@Component
public class WebhookErrorCategorizer {

    public String categorize(Exception e) {
        if (e instanceof ConnectException) return "Connection error";
        if (e instanceof SocketTimeoutException) return "Timeout";
        if (e instanceof ResourceAccessException rae
                && rae.getCause() instanceof SocketTimeoutException) return "Timeout";
        if (e instanceof ResourceAccessException) return "Network error";
        return "HTTP error";
    }
}
