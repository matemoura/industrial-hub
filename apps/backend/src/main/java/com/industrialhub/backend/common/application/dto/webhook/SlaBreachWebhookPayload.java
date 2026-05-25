package com.industrialhub.backend.common.application.dto.webhook;

import java.time.LocalDateTime;
import java.util.UUID;

public record SlaBreachWebhookPayload(
    UUID slaRuleId,
    String entityType,
    String entityId,
    String breachLevel,
    LocalDateTime detectedAt
) {}
