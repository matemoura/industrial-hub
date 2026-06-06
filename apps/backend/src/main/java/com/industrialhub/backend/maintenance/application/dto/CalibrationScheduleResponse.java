package com.industrialhub.backend.maintenance.application.dto;

import com.industrialhub.backend.maintenance.domain.CalibrationSchedule;

import java.time.LocalDate;
import java.util.UUID;

public record CalibrationScheduleResponse(
    UUID id,
    UUID equipmentId,
    String equipmentCode,
    String equipmentName,
    int intervalDays,
    LocalDate lastCalibratedAt,
    LocalDate nextDueAt,
    boolean overdue,
    String externalProvider,
    boolean active
) {
    public static CalibrationScheduleResponse from(CalibrationSchedule s) {
        boolean overdue = s.getNextDueAt() != null && s.getNextDueAt().isBefore(LocalDate.now());
        return new CalibrationScheduleResponse(
            s.getId(),
            s.getEquipment().getId(),
            s.getEquipment().getCode(),
            s.getEquipment().getName(),
            s.getIntervalDays(),
            s.getLastCalibratedAt(),
            s.getNextDueAt(),
            overdue,
            s.getExternalProvider(),
            s.isActive()
        );
    }
}
