package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.ImportProductionBatchResponse;
import com.industrialhub.backend.production.domain.ImportProductionBatchNotFoundException;
import com.industrialhub.backend.production.domain.ProductionImportType;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GetImportHistoryUseCase {

    private final ImportProductionBatchRepository batchRepository;

    public GetImportHistoryUseCase(ImportProductionBatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    public Page<ImportProductionBatchResponse> list(ProductionImportType type, Pageable pageable) {
        return batchRepository.findFiltered(type, pageable)
                .map(b -> ImportProductionBatchResponse.from(b, null));
    }

    public ImportProductionBatchResponse getById(UUID id) {
        return batchRepository.findById(id)
                .map(b -> ImportProductionBatchResponse.from(b, null))
                .orElseThrow(() -> new ImportProductionBatchNotFoundException(id));
    }
}
