package com.industrialhub.backend.oee.domain;

import java.util.UUID;

public class PlannedDowntimeNotFoundException extends RuntimeException {

    public PlannedDowntimeNotFoundException(UUID id) {
        super("Parada planejada não encontrada: " + id);
    }
}
