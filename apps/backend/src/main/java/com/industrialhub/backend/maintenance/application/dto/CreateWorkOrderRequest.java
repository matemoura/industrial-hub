package com.industrialhub.backend.maintenance.application.dto;

import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateWorkOrderRequest(
    @NotNull UUID equipmentId,
    @NotNull WorkOrderType type,
    @NotBlank @Size(max = 200) String title,
    String description,
    @NotNull WorkOrderPriority priority,
    @Size(max = 50) String assignedTo
) {}
