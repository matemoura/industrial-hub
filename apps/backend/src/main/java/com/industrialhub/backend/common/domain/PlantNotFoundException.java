package com.industrialhub.backend.common.domain;

import java.util.UUID;

public class PlantNotFoundException extends RuntimeException {

    public PlantNotFoundException(UUID id) {
        super("Planta não encontrada: " + id);
    }

    public PlantNotFoundException(String code) {
        super("Planta não encontrada com código: " + code);
    }
}
