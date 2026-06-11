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

            // Accept internal column names OR Dynamics Portuguese export column names
            String headerError = ExcelParsingHelper.validateRequiredColumnsAliased(colIndex,
                    new String[]{"dynamics_code", "número_do_item"});
            if (headerError != null) {
                errors.add(new ImportErrorDto(1, headerError));
                return saveBatch(fileName, username, total, created, updated, errors);
            }

            // Pre-load all existing products and families into maps — avoids N+1 queries
            Map<String, Product>       existingProducts = new HashMap<>();
            Map<String, ProductFamily> existingFamilies = new HashMap<>();
            productRepository.findAll().forEach(p -> existingProducts.put(p.getDynamicsCode(), p));
            familyRepository.findAll().forEach(f -> existingFamilies.put(f.getCode(), f));

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                total++;

                try {
                    String dynamicsCode = getStringByAliases(row, colIndex,
                            "dynamics_code", "número_do_item");
                    if (dynamicsCode == null || dynamicsCode.isBlank()) {
                        errors.add(new ImportErrorDto(i + 1, "Código do item ausente ou nulo"));
                        continue;
                    }
                    dynamicsCode = dynamicsCode.trim();

                    String typeStr = getStringByAliases(row, colIndex,
                            "type", "tipo_de_produto", "tipo_de_produto/serviço", "subtipo_do_produto");
                    ProductType type = parseProductType(typeStr);

                    String name = getStringByAliases(row, colIndex,
                            "name", "nome_do_produto", "pesquisar_nome");
                    String familyCode = getStringByAliases(row, colIndex,
                            "family_code", "grupos_de_dimensões_de_produto");
                    String familyName = getStringByAliases(row, colIndex,
                            "family_name", "grupos_de_dimensões_de_produto");
                    String unit = getStringByAliases(row, colIndex,
                            "unit", "unidade_de_estoque");
                    String sterilizationStr = getString(row, colIndex, "requires_sterilization");
                    boolean requiresSterilization = "true".equalsIgnoreCase(sterilizationStr)
                            || "1".equals(sterilizationStr) || "sim".equalsIgnoreCase(sterilizationStr);

                    // Derive family from column value or fall back to product code prefix
                    if (familyCode == null || familyCode.isBlank()) {
                        familyCode = deriveFamilyCode(dynamicsCode);
                        if (familyName == null || familyName.isBlank()) familyName = familyCode;
                    }

                    // Upsert family from in-memory map — no DB query per row
                    ProductFamily family = null;
                    if (type != ProductType.RAW_MATERIAL && familyCode != null && !familyCode.isBlank()) {
                        final String fc = familyCode.trim();
                        final String fn = familyName != null ? familyName.trim() : fc;
                        family = existingFamilies.computeIfAbsent(fc,
                                k -> familyRepository.save(
                                        ProductFamily.builder().code(k).name(fn).active(true).build()));
                    }

                    // Upsert product from in-memory map — no DB query per row
                    Product existing = existingProducts.get(dynamicsCode);
                    if (existing != null) {
                        existing.setName(name != null ? name.trim() : existing.getName());
                        existing.setType(type);
                        existing.setUnit(unit != null ? unit.trim() : existing.getUnit());
                        existing.setRequiresSterilization(requiresSterilization);
                        existing.setLastSyncAt(LocalDateTime.now());
                        if (family != null) existing.setFamily(family);
                        // Hub-managed fields (leadTimeDays, minStockQty, batchSize) are NOT updated here
                        productRepository.save(existing);
                        updated++;
                    } else {
                        Product p = Product.builder()
                                .dynamicsCode(dynamicsCode)
                                .name(name != null ? name.trim() : dynamicsCode)
                                .type(type)
                                .family(family)
                                .unit(unit != null ? unit.trim() : null)
                                .requiresSterilization(requiresSterilization)
                                .active(true)
                                .lastSyncAt(LocalDateTime.now())
                                .build();
                        p = productRepository.save(p);
                        existingProducts.put(dynamicsCode, p);
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
            case STRING -> { String v = cell.getStringCellValue().trim(); yield v.isEmpty() ? null : v; }
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private String getStringByAliases(Row row, Map<String, Integer> colIndex, String... aliases) {
        for (String alias : aliases) {
            String v = getString(row, colIndex, alias);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    /**
     * Derives a family code from the product code prefix (leading letters).
     * Examples: "MP0000001" → "MP", "UC0000022" → "UC", "50503" → "MSB"
     */
    private String deriveFamilyCode(String dynamicsCode) {
        if (dynamicsCode == null || dynamicsCode.isBlank()) return "OUTROS";
        StringBuilder prefix = new StringBuilder();
        for (char c : dynamicsCode.toCharArray()) {
            if (Character.isLetter(c)) prefix.append(c);
            else break;
        }
        return prefix.length() > 0 ? prefix.toString().toUpperCase() : "MSB";
    }

    /** Translates Dynamics product type values (Portuguese or English) to the internal enum. */
    private ProductType parseProductType(String s) {
        if (s == null) return ProductType.FINISHED;
        try {
            return ProductType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {}
        String norm = s.trim().toLowerCase();
        if (norm.contains("mat") && (norm.contains("prim") || norm.contains("raw"))) return ProductType.RAW_MATERIAL;
        if (norm.contains("inter") || norm.contains("semi")) return ProductType.INTERMEDIATE;
        return ProductType.FINISHED;
    }
}
