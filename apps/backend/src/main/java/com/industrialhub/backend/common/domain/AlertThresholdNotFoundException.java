package com.industrialhub.backend.common.domain;

import java.util.UUID;

public class AlertThresholdNotFoundException extends RuntimeException {

    public AlertThresholdNotFoundException(UUID id) {
        super("Alert threshold não encontrado: " + id);
    }
}
