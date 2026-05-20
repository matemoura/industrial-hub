package com.industrialhub.backend.common.application.dto;

import com.industrialhub.backend.common.domain.Shift;

import java.time.LocalTime;
import java.util.UUID;

public record ShiftResponse(
        UUID id,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        boolean overnight,
        boolean active
) {
    public static ShiftResponse from(Shift s) {
        return new ShiftResponse(
                s.getId(),
                s.getName(),
                s.getStartTime(),
                s.getEndTime(),
                s.isOvernight(),
                s.isActive()
        );
    }
}
