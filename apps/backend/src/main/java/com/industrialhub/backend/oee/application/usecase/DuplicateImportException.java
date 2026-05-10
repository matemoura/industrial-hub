package com.industrialhub.backend.oee.application.usecase;

import java.time.LocalDate;
import java.util.UUID;

public class DuplicateImportException extends RuntimeException {

    private final UUID existingBatchId;
    private final LocalDate periodDate;

    public DuplicateImportException(UUID existingBatchId, LocalDate periodDate) {
        super("Dados de " + periodDate + " já importados");
        this.existingBatchId = existingBatchId;
        this.periodDate = periodDate;
    }

    public UUID getExistingBatchId() { return existingBatchId; }
    public LocalDate getPeriodDate() { return periodDate; }
}
