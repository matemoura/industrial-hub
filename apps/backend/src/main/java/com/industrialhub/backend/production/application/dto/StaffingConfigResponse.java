package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.StaffingConfig;

import java.time.LocalDateTime;
import java.util.UUID;

public record StaffingConfigResponse(
        UUID id,
        Integer shiftHours,
        Integer shiftsPerDay,
        LocalDateTime updatedAt,
        String updatedBy
) {
    public static StaffingConfigResponse from(StaffingConfig sc) {
        return new StaffingConfigResponse(
                sc.getId(),
                sc.getShiftHours(),
                sc.getShiftsPerDay(),
                sc.getUpdatedAt(),
                sc.getUpdatedBy()
        );
    }
}
