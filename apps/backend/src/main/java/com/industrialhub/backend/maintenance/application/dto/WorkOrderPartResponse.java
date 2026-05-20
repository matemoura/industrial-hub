package com.industrialhub.backend.maintenance.application.dto;

import com.industrialhub.backend.maintenance.domain.WorkOrderPart;

import java.time.LocalDateTime;
import java.util.UUID;

public record WorkOrderPartResponse(
    UUID id,
    UUID sparePartId,
    String sparePartCode,
    String sparePartName,
    Integer quantity,
    String addedBy,
    LocalDateTime addedAt
) {
    public static WorkOrderPartResponse from(WorkOrderPart wop) {
        return new WorkOrderPartResponse(
            wop.getId(),
            wop.getSparePart().getId(),
            wop.getSparePart().getCode(),
            wop.getSparePart().getName(),
            wop.getQuantity(),
            wop.getAddedBy(),
            wop.getAddedAt()
        );
    }
}
