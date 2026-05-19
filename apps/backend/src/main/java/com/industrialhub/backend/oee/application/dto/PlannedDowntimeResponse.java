package com.industrialhub.backend.oee.application.dto;

import com.industrialhub.backend.oee.domain.DowntimeReason;
import com.industrialhub.backend.oee.domain.PlannedDowntime;

import java.time.LocalDateTime;
import java.util.UUID;

public record PlannedDowntimeResponse(
        UUID id,
        UUID equipmentId,
        String equipmentCode,
        String equipmentName,
        DowntimeReason reason,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int durationMinutes,
        String description,
        String registeredBy,
        LocalDateTime registeredAt
) {
    public static PlannedDowntimeResponse from(PlannedDowntime pd) {
        return new PlannedDowntimeResponse(
                pd.getId(),
                pd.getEquipment() != null ? pd.getEquipment().getId() : null,
                pd.getEquipment() != null ? pd.getEquipment().getCode() : null,
                pd.getEquipment() != null ? pd.getEquipment().getName() : null,
                pd.getReason(),
                pd.getStartAt(),
                pd.getEndAt(),
                pd.getDurationMinutes(),
                pd.getDescription(),
                pd.getRegisteredBy(),
                pd.getRegisteredAt()
        );
    }
}
