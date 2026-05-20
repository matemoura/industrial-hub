package com.industrialhub.backend.common.domain;

import java.util.UUID;

public class ShiftNotFoundException extends RuntimeException {

    public ShiftNotFoundException(UUID id) {
        super("Turno não encontrado: " + id);
    }
}
