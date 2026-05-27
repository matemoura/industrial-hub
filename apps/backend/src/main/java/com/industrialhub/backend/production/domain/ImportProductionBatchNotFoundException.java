package com.industrialhub.backend.production.domain;

import java.util.UUID;

public class ImportProductionBatchNotFoundException extends RuntimeException {
    public ImportProductionBatchNotFoundException(UUID id) {
        super("Lote de importação não encontrado: " + id);
    }
}
