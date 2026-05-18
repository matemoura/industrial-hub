package com.industrialhub.backend.maintenance.application.dto;

import com.industrialhub.backend.maintenance.domain.MaintenanceSchedule;
import com.industrialhub.backend.maintenance.domain.ScheduleRecurrence;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ScheduleResponse(
    UUID id,
    UUID equipmentId,
    String equipmentCode,
    String equipmentName,
    String title,
    String description,
    WorkOrderPriority priority,
    ScheduleRecurrence recurrence,
    Integer dayOfWeek,
    Integer dayOfMonth,
    LocalDate nextRunAt,
    LocalDate lastRunAt,
    boolean active,
    String createdBy,
    LocalDateTime createdAt
) {
    public static ScheduleResponse from(MaintenanceSchedule s) {
        return new ScheduleResponse(
            s.getId(),
            s.getEquipment().getId(),
            s.getEquipment().getCode(),
            s.getEquipment().getName(),
            s.getTitle(),
            s.getDescription(),
            s.getPriority(),
            s.getRecurrence(),
            s.getDayOfWeek(),
            s.getDayOfMonth(),
            s.getNextRunAt(),
            s.getLastRunAt(),
            s.isActive(),
            s.getCreatedBy(),
            s.getCreatedAt()
        );
    }
}
