package com.industrialhub.backend.maintenance.domain;

import java.util.UUID;

public class WorkOrderNotFoundException extends RuntimeException {

    public WorkOrderNotFoundException(UUID id) {
        super("Work order not found: " + id);
    }
}
