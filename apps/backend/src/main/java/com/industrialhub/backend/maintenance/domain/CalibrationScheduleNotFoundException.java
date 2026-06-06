package com.industrialhub.backend.maintenance.domain;

import java.util.UUID;

public class CalibrationScheduleNotFoundException extends RuntimeException {

    public CalibrationScheduleNotFoundException(UUID id) {
        super("Plano de calibração não encontrado: " + id);
    }
}
