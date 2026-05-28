package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.production.application.dto.ImportErrorDto;
import com.industrialhub.backend.production.application.dto.ImportProductionBatchResponse;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import com.industrialhub.backend.production.infrastructure.ProductRepository;
import com.industrialhub.backend.production.infrastructure.StockSnapshotRepository;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ImportStockSnapshotUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImportStockSnapshotUseCase.class);

    private final ProductRepository productRepository;
    private final StockSnapshotRepository stockSnapshotRepository;
    private final ImportProductionBatchRepository batchRepository;
    private final AuditService auditService;

    public ImportStockSnapshotUseCase(ProductRepository productRepository,
                                       StockSnapshotRepository stockSnapshotRepository,
                                       ImportProductionBatchRepository batchRepository,
                                       AuditService auditService) {
        this.productRepository = productRepository;
        this.stockSnapshotRepository = stockSnapshotRepository;
        this.batchRepository = batchRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ImportProductionBatchResponse execute(MultipartFile file, String username) {
        // SEC-107: validate MIME type via Tika before processing
        try {
            ExcelFileValidator.validate(file);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Arquivo inválido: apenas Excel (.xlsx, .xls) é aceito");
        }

        String rawName = file.getOriginalFilename();
        String fileName = rawName != null ? rawName.replaceAll("[\r\n\t]", "_") : "unknown";

        List<ImportErrorDto> errors = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int total = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                errors.add(new ImportErrorDto(0, "Planilha vazia ou sem cabeçalho"));
                return saveBatch(fileName, username, total, created, updated, errors, null);
            }

            Map<String, Integer> colIndex = ExcelParsingHelper.buildColumnIndex(header);
            ImportProductionBatch batch = null; // will be created after counting

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                total++;

                try {
                    String dynamicsCode = ExcelParsingHelper.getString(row, colIndex, "dynamics_code");
                    if (dynamicsCode == null || dynamicsCode.isBlank()) {
                        errors.add(new ImportErrorDto(i + 1, "dynamics_code ausente"));
                        continue;
                    }

                    Integer qty = ExcelParsingHelper.getInteger(row, colIndex, "qty");
                    if (qty == null || qty < 0) {
                        errors.add(new ImportErrorDto(i + 1, "qty inválido: " + qty));
                        continue;
                    }

                    LocalDate snapshotDate = ExcelParsingHelper.getLocalDate(row, colIndex, "snapshot_date");
                    if (snapshotDate == null) {
                        errors.add(new ImportErrorDto(i + 1, "snapshot_date ausente ou inválido"));
                        continue;
                    }

                    Optional<Product> productOpt = productRepository.findByDynamicsCode(dynamicsCode.trim());
                    if (productOpt.isEmpty()) {
                        errors.add(new ImportErrorDto(i + 1, "Produto não encontrado: " + dynamicsCode));
                        continue;
                    }

                    Product product = productOpt.get();
                    Optional<StockSnapshot> existing = stockSnapshotRepository
                            .findByProductIdAndSnapshotDate(product.getId(), snapshotDate);

                    if (existing.isPresent()) {
                        StockSnapshot s = existing.get();
                        s.setQty(qty);
                        s.setImportedAt(LocalDateTime.now());
                        s.setImportedBy(username);
                        stockSnapshotRepository.save(s);
                        updated++;
                    } else {
                        stockSnapshotRepository.save(StockSnapshot.builder()
                                .product(product)
                                .qty(qty)
                                .snapshotDate(snapshotDate)
                                .importedAt(LocalDateTime.now())
                                .importedBy(username)
                                .build());
                        created++;
                    }
                } catch (Exception e) {
                    // SEC-108: sanitize unexpected exceptions — never expose stack trace or DB messages
                    log.warn("Erro linha {}: {}", i + 1, e.getMessage());
                    errors.add(new ImportErrorDto(i + 1, "Erro ao processar linha %d".formatted(i + 1)));
                }
            }
        } catch (IOException e) {
            log.warn("Erro ao processar arquivo Excel na importação de stock: {}", e.getMessage());
            errors.add(new ImportErrorDto(0, "Erro ao processar o arquivo Excel. Verifique o formato e tente novamente."));
        }

        return saveBatch(fileName, username, total, created, updated, errors, null);
    }

    private ImportProductionBatchResponse saveBatch(String fileName, String username,
                                                      int total, int created, int updated,
                                                      List<ImportErrorDto> errors,
                                                      ImportProductionBatch batchRef) {
        ImportProductionBatch batch = batchRepository.save(ImportProductionBatch.builder()
                .type(ProductionImportType.STOCK)
                .fileName(fileName)
                .importedAt(LocalDateTime.now())
                .importedBy(username)
                .totalRecords(total)
                .createdRecords(created)
                .updatedRecords(updated)
                .errorRecords(errors.size())
                .build());

        auditService.log(username, AuditAction.PRODUCTION_IMPORT, "ImportProductionBatch",
                batch.getId().toString(),
                Map.of("type", "STOCK", "created", created, "updated", updated, "errors", errors.size()));

        return ImportProductionBatchResponse.from(batch, errors);
    }
}
