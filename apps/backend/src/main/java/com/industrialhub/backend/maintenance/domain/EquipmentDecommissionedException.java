package com.industrialhub.backend.maintenance.domain;

import java.util.UUID;

public class EquipmentDecommissionedException extends RuntimeException {

    public EquipmentDecommissionedException(UUID equipmentId) {
        super("Equipamento descomissionado não pode ter plano de calibração: " + equipmentId);
    }
}
