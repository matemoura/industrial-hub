package com.industrialhub.backend.common.application.dto.webhook;

import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;

import java.util.UUID;

public record WorkOrderStatusChangedWebhookPayload(
    UUID id,
    String title,
    WorkOrderStatus previousStatus,
    WorkOrderStatus newStatus,
    String changedBy
) {}
