package com.industrialhub.backend.maintenance.domain;

import java.util.UUID;

public class DecommissionedEquipmentCalibrationException extends RuntimeException {

    public DecommissionedEquipmentCalibrationException(UUID equipmentId) {
        super("Equipamento descomissionado não pode ter plano de calibração: " + equipmentId);
    }
}
