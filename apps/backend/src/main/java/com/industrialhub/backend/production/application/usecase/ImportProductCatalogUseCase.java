package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.production.application.dto.ImportErrorDto;
import com.industrialhub.backend.production.application.dto.ImportProductionBatchResponse;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import com.industrialhub.backend.production.infrastructure.ProductFamilyRepository;
import com.industrialhub.backend.production.infrastructure.ProductRepository;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ImportProductCatalogUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImportProductCatalogUseCase.class);

    private final ProductRepository productRepository;
    private final ProductFamilyRepository familyRepository;
    private final ImportProductionBatchRepository batchRepository;
    private final AuditService auditService;

    public ImportProductCatalogUseCase(ProductRepository productRepository,
                                        ProductFamilyRepository familyRepository,
                                        ImportProductionBatchRepository batchRepository,
                                        AuditService auditService) {
        this.productRepository = productRepository;
        this.familyRepository = familyRepository;
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
            // Determine column indices from header row
            Row header = sheet.getRow(0);
            if (header == null) {
                errors.add(new ImportErrorDto(0, "Planilha vazia ou sem cabeçalho"));
                return saveBatch(fileName, username, total, created, updated, errors);
            }

            Map<String, Integer> colIndex = buildColumnIndex(header);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                total++;

                try {
                    String dynamicsCode = getString(row, colIndex, "dynamics_code");
                    if (dynamicsCode == null || dynamicsCode.isBlank()) {
                        errors.add(new ImportErrorDto(i + 1, "dynamics_code ausente ou nulo"));
                        continue;
                    }

                    String typeStr = getString(row, colIndex, "type");
                    ProductType type;
                    try {
                        type = ProductType.valueOf(typeStr != null ? typeStr.trim().toUpperCase() : "");
                    } catch (IllegalArgumentException e) {
                        errors.add(new ImportErrorDto(i + 1, "type inválido: " + typeStr));
                        continue;
                    }

                    String name = getString(row, colIndex, "name");
                    String familyCode = getString(row, colIndex, "family_code");
                    String familyName = getString(row, colIndex, "family_name");
                    String unit = getString(row, colIndex, "unit");
                    String sterilizationStr = getString(row, colIndex, "requires_sterilization");
                    boolean requiresSterilization = "true".equalsIgnoreCase(sterilizationStr)
                            || "1".equals(sterilizationStr) || "sim".equalsIgnoreCase(sterilizationStr);

                    // Upsert family — unless RAW_MATERIAL type
                    ProductFamily family = null;
                    if (type != ProductType.RAW_MATERIAL && familyCode != null && !familyCode.isBlank()) {
                        final String fc = familyCode.trim();
                        final String fn = familyName != null ? familyName.trim() : fc;
                        family = familyRepository.findByCode(fc)
                                .orElseGet(() -> familyRepository.save(
                                        ProductFamily.builder().code(fc).name(fn).active(true).build()));
                    }

                    // Upsert product
                    Optional<Product> existing = productRepository.findByDynamicsCode(dynamicsCode.trim());
                    if (existing.isPresent()) {
                        Product p = existing.get();
                        p.setName(name != null ? name.trim() : p.getName());
                        p.setType(type);
                        p.setUnit(unit != null ? unit.trim() : p.getUnit());
                        p.setRequiresSterilization(requiresSterilization);
                        p.setLastSyncAt(LocalDateTime.now());
                        if (family != null) {
                            p.setFamily(family);
                        }
                        // Hub-managed fields (leadTimeDays, minStockQty, batchSize) are NOT updated here
                        productRepository.save(p);
                        updated++;
                    } else {
                        Product p = Product.builder()
                                .dynamicsCode(dynamicsCode.trim())
                                .name(name != null ? name.trim() : dynamicsCode)
                                .type(type)
                                .family(family)
                                .unit(unit != null ? unit.trim() : null)
                                .requiresSterilization(requiresSterilization)
                                .active(true)
                                .lastSyncAt(LocalDateTime.now())
                                .build();
                        productRepository.save(p);
                        created++;
                    }
                } catch (Exception e) {
                    // SEC-108: sanitize unexpected exceptions — never expose stack trace or DB messages
                    log.warn("Erro ao processar linha {} da importação de produtos: {}", i + 1, e.getMessage(), e);
                    errors.add(new ImportErrorDto(i + 1, "Erro ao processar linha %d".formatted(i + 1)));
                }
            }
        } catch (IOException e) {
            log.warn("Erro ao processar arquivo Excel na importação de produtos: {}", e.getMessage(), e);
            errors.add(new ImportErrorDto(0, "Erro ao processar o arquivo Excel. Verifique o formato e tente novamente."));
        }

        return saveBatch(fileName, username, total, created, updated, errors);
    }

    private ImportProductionBatchResponse saveBatch(String fileName, String username,
                                                     int total, int created, int updated,
                                                     List<ImportErrorDto> errors) {
        ImportProductionBatch batch = batchRepository.save(ImportProductionBatch.builder()
                .type(ProductionImportType.PRODUCT_CATALOG)
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
                Map.of("type", "PRODUCT_CATALOG", "created", created, "updated", updated, "errors", errors.size()));

        return ImportProductionBatchResponse.from(batch, errors);
    }

    private Map<String, Integer> buildColumnIndex(Row header) {
        Map<String, Integer> index = new HashMap<>();
        for (int c = 0; c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            if (cell != null) {
                String key = cell.getStringCellValue().trim().toLowerCase().replace(" ", "_");
                index.put(key, c);
            }
        }
        return index;
    }

    private String getString(Row row, Map<String, Integer> colIndex, String colName) {
        Integer idx = colIndex.get(colName);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }
}
