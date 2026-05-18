package com.industrialhub.backend.maintenance.domain;

import java.util.UUID;

public class ScheduleNotFoundException extends RuntimeException {
    public ScheduleNotFoundException(UUID id) {
        super("Plano de manutenção não encontrado: " + id);
    }
}
