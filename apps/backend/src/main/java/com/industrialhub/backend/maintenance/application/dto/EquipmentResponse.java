package com.industrialhub.backend.maintenance.application.dto;

import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.EquipmentType;

import java.time.LocalDate;
import java.util.UUID;

public record EquipmentResponse(
    UUID id,
    String code,
    String name,
    String location,
    EquipmentType type,
    EquipmentStatus status,
    LocalDate acquiredAt,
    boolean active
) {
    public static EquipmentResponse from(Equipment e) {
        return new EquipmentResponse(
            e.getId(),
            e.getCode(),
            e.getName(),
            e.getLocation(),
            e.getType(),
            e.getStatus(),
            e.getAcquiredAt(),
            e.isActive()
        );
    }
}
