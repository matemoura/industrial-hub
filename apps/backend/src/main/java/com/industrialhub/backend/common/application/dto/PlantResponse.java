package com.industrialhub.backend.common.application.dto;

import com.industrialhub.backend.common.domain.Plant;

import java.time.LocalDateTime;
import java.util.UUID;

public record PlantResponse(
    UUID id,
    String code,
    String name,
    String address,
    String timezone,
    boolean active,
    boolean isDefault,
    LocalDateTime createdAt
) {
    public static PlantResponse from(Plant plant) {
        return new PlantResponse(
            plant.getId(),
            plant.getCode(),
            plant.getName(),
            plant.getAddress(),
            plant.getTimezone(),
            plant.isActive(),
            plant.isDefault(),
            plant.getCreatedAt()
        );
    }
}
