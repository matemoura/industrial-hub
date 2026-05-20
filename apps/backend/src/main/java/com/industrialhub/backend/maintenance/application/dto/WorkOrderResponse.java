package com.industrialhub.backend.maintenance.application.dto;

import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;

import java.time.LocalDateTime;
import java.util.UUID;

public record WorkOrderResponse(
    UUID id,
    UUID equipmentId,
    String equipmentCode,
    String equipmentName,
    WorkOrderType type,
    String title,
    String description,
    WorkOrderPriority priority,
    WorkOrderStatus status,
    String assignedTo,
    String openedBy,
    LocalDateTime openedAt,
    LocalDateTime startedAt,
    LocalDateTime closedAt,
    UUID scheduleId,
    UUID shiftId,
    String shiftName
) {
    public static WorkOrderResponse from(WorkOrder wo) {
        return new WorkOrderResponse(
            wo.getId(),
            wo.getEquipment().getId(),
            wo.getEquipment().getCode(),
            wo.getEquipment().getName(),
            wo.getType(),
            wo.getTitle(),
            wo.getDescription(),
            wo.getPriority(),
            wo.getStatus(),
            wo.getAssignedTo(),
            wo.getOpenedBy(),
            wo.getOpenedAt(),
            wo.getStartedAt(),
            wo.getClosedAt(),
            wo.getSchedule() != null ? wo.getSchedule().getId() : null,
            wo.getShift() != null ? wo.getShift().getId() : null,
            wo.getShift() != null ? wo.getShift().getName() : null
        );
    }
}
