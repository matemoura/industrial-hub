package com.industrialhub.backend.common.application.dto.webhook;

import com.industrialhub.backend.qms.domain.NcStatus;

import java.util.UUID;

public record NcStatusChangedWebhookPayload(
    UUID id,
    String title,
    NcStatus previousStatus,
    NcStatus newStatus,
    String changedBy
) {}
