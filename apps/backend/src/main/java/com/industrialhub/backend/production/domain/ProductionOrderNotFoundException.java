package com.industrialhub.backend.production.domain;

import java.util.UUID;

public class ProductionOrderNotFoundException extends RuntimeException {
    public ProductionOrderNotFoundException(UUID id) {
        super("Ordem de produção não encontrada: " + id);
    }
}
