package com.industrialhub.backend.common.application.dto.webhook;

import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentType;

import java.util.UUID;

public record EquipmentWebhookPayload(
    UUID equipmentId,
    String equipmentCode,
    String equipmentName,
    EquipmentType equipmentType
) {
    public static EquipmentWebhookPayload from(Equipment equipment) {
        return new EquipmentWebhookPayload(
            equipment.getId(),
            equipment.getCode(),
            equipment.getName(),
            equipment.getType()
        );
    }
}
