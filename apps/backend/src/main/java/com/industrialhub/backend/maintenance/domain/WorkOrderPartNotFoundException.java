package com.industrialhub.backend.maintenance.domain;

import java.util.UUID;

public class WorkOrderPartNotFoundException extends RuntimeException {
    public WorkOrderPartNotFoundException(UUID id) {
        super("Consumo de peça não encontrado: " + id);
    }
}
