package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.ImportProductionBatch;
import com.industrialhub.backend.production.domain.ProductionImportType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// SEC-109: importedBy removed — available only via audit log (ADMIN-only). See ADR-041 Decisão 7.
public record ImportProductionBatchResponse(
        UUID id,
        ProductionImportType type,
        String fileName,
        LocalDateTime importedAt,
        int totalRecords,
        int createdRecords,
        int updatedRecords,
        int errorRecords,
        List<ImportErrorDto> errors
) {
    public static ImportProductionBatchResponse from(ImportProductionBatch batch, List<ImportErrorDto> errors) {
        return new ImportProductionBatchResponse(
                batch.getId(),
                batch.getType(),
                batch.getFileName(),
                batch.getImportedAt(),
                batch.getTotalRecords() != null ? batch.getTotalRecords() : 0,
                batch.getCreatedRecords() != null ? batch.getCreatedRecords() : 0,
                batch.getUpdatedRecords() != null ? batch.getUpdatedRecords() : 0,
                batch.getErrorRecords() != null ? batch.getErrorRecords() : 0,
                errors != null ? errors : List.of()
        );
    }
}
