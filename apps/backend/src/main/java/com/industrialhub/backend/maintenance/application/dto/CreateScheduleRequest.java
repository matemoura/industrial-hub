package com.industrialhub.backend.maintenance.application.dto;

import com.industrialhub.backend.maintenance.domain.ScheduleRecurrence;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateScheduleRequest(
    @NotNull UUID equipmentId,
    @NotBlank @Size(max = 200) String title,
    @Size(max = 2000) String description,
    @NotNull WorkOrderPriority priority,
    @NotNull ScheduleRecurrence recurrence,
    Integer dayOfWeek,
    Integer dayOfMonth
) {}
