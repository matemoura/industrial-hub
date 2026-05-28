package com.industrialhub.backend.production.domain;

import java.util.UUID;

public class SterilizationLoadNotFoundException extends RuntimeException {
    public SterilizationLoadNotFoundException(UUID id) {
        super("Carga de esterilização não encontrada: " + id);
    }
}
