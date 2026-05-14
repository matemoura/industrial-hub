package com.industrialhub.backend.maintenance.application.dto;

import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import jakarta.validation.constraints.NotNull;

public record TransitionWorkOrderStatusRequest(
    @NotNull WorkOrderStatus status
) {}
