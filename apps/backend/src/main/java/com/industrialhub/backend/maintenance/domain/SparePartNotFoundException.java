package com.industrialhub.backend.maintenance.domain;

import java.util.UUID;

public class SparePartNotFoundException extends RuntimeException {
    public SparePartNotFoundException(UUID id) {
        super("Peça não encontrada: " + id);
    }
}
