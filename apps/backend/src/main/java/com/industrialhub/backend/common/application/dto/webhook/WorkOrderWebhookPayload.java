package com.industrialhub.backend.common.application.dto.webhook;

import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;

import java.time.LocalDateTime;
import java.util.UUID;

public record WorkOrderWebhookPayload(
    UUID id,
    String title,
    WorkOrderType type,
    WorkOrderPriority priority,
    WorkOrderStatus status,
    UUID equipmentId,
    String openedBy,
    LocalDateTime openedAt
) {
    public static WorkOrderWebhookPayload from(WorkOrder wo) {
        return new WorkOrderWebhookPayload(
            wo.getId(),
            wo.getTitle(),
            wo.getType(),
            wo.getPriority(),
            wo.getStatus(),
            wo.getEquipment() != null ? wo.getEquipment().getId() : null,
            wo.getOpenedBy(),
            wo.getOpenedAt()
        );
    }
}
