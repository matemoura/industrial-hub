package com.industrialhub.backend.production.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Resposta da importação de BOM (ADR-044).
 * Separada do ImportProductionBatchResponse pois o BOM não usa ImportProductionBatch.
 */
public record BomImportResponse(
        int totalRecords,
        int created,
        int updated,
        int errors,
        String importedBy,
        LocalDateTime importedAt,
        List<ImportErrorDto> errorDetails
) {}
