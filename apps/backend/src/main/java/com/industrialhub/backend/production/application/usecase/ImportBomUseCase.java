package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.production.application.dto.BomImportResponse;
import com.industrialhub.backend.production.application.dto.ImportErrorDto;
import com.industrialhub.backend.production.domain.Product;
import com.industrialhub.backend.production.domain.ProductComponent;
import com.industrialhub.backend.production.infrastructure.ProductComponentRepository;
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

/**
 * ADR-044 Decisões 1, 2, 4 — importação de BOM via Excel com substituição total por produto pai.
 * Colunas esperadas: parent_code, component_code, quantity, unit.
 */
@Service
public class ImportBomUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImportBomUseCase.class);

    private final ProductRepository productRepository;
    private final ProductComponentRepository componentRepository;
    private final AuditService auditService;

    public ImportBomUseCase(ProductRepository productRepository,
                             ProductComponentRepository componentRepository,
                             AuditService auditService) {
        this.productRepository = productRepository;
        this.componentRepository = componentRepository;
        this.auditService = auditService;
    }

    @Transactional
    public BomImportResponse execute(MultipartFile file, String username) {
        // SEC-107: validate MIME type via Tika (consistent with other import use cases)
        try {
            ExcelFileValidator.validate(file);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("Arquivo inválido: apenas Excel (.xlsx, .xls) é aceito");
        }

        List<ImportErrorDto> errors = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int total = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                errors.add(new ImportErrorDto(0, "Planilha vazia ou sem cabeçalho"));
                return buildResponse(total, created, updated, errors, username);
            }

            Map<String, Integer> colIndex = ExcelParsingHelper.buildColumnIndex(header);

            // Validate required columns
            List<String> required = List.of("parent_code", "component_code", "quantity", "unit");
            for (String col : required) {
                if (!colIndex.containsKey(col)) {
                    errors.add(new ImportErrorDto(0, "Coluna obrigatória ausente: " + col));
                    return buildResponse(total, created, updated, errors, username);
                }
            }

            // ADR-044 Decisão 2: group rows by parentCode; process each parent atomically
            // First pass: collect all rows per parent_code
            Map<String, List<BomRow>> rowsByParent = new LinkedHashMap<>();
            Map<Integer, String> rowErrors = new LinkedHashMap<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                total++;

                String parentCode = ExcelParsingHelper.getString(row, colIndex, "parent_code");
                String componentCode = ExcelParsingHelper.getString(row, colIndex, "component_code");
                Double quantity = ExcelParsingHelper.getDouble(row, colIndex, "quantity");
                String unit = ExcelParsingHelper.getString(row, colIndex, "unit");

                if (parentCode == null || parentCode.isBlank()) {
                    rowErrors.put(i, "parent_code ausente na linha " + (i + 1));
                    continue;
                }
                if (componentCode == null || componentCode.isBlank()) {
                    rowErrors.put(i, "component_code ausente na linha " + (i + 1));
                    continue;
                }
                if (quantity == null || quantity <= 0) {
                    rowErrors.put(i, "quantity inválida na linha " + (i + 1));
                    continue;
                }

                rowsByParent
                        .computeIfAbsent(parentCode.trim(), k -> new ArrayList<>())
                        .add(new BomRow(i, parentCode.trim(), componentCode.trim(), quantity,
                                unit != null ? unit.trim() : "UN"));
            }

            // Add parse errors
            rowErrors.forEach((line, msg) -> errors.add(new ImportErrorDto(line + 1, msg)));

            // Second pass: for each parentCode, validate parent product, then replace BOM
            for (Map.Entry<String, List<BomRow>> entry : rowsByParent.entrySet()) {
                String parentCode = entry.getKey();
                List<BomRow> rows = entry.getValue();

                Optional<Product> parentOpt = productRepository.findByDynamicsCode(parentCode);
                if (parentOpt.isEmpty()) {
                    rows.forEach(r -> errors.add(
                            new ImportErrorDto(r.line() + 1,
                                    "Produto pai não encontrado: " + parentCode)));
                    continue;
                }
                Product parentProduct = parentOpt.get();

                // ADR-044 Decisão 2: delete existing BOM for this parent before inserting
                componentRepository.deleteByParentProductCode(parentCode);

                for (BomRow bomRow : rows) {
                    try {
                        Optional<Product> compOpt = productRepository.findByDynamicsCode(bomRow.componentCode());
                        if (compOpt.isEmpty()) {
                            errors.add(new ImportErrorDto(bomRow.line() + 1,
                                    "Produto componente não encontrado: " + bomRow.componentCode()));
                            continue;
                        }
                        Product componentProduct = compOpt.get();

                        // Determine level based on component type (INTERMEDIATE = could be 2, others = 1)
                        int level = 1;

                        ProductComponent pc = ProductComponent.builder()
                                .parentProduct(parentProduct)
                                .componentProduct(componentProduct)
                                .quantity(bomRow.quantity())
                                .unit(bomRow.unit())
                                .level(level)
                                .active(true)
                                .build();

                        componentRepository.save(pc);
                        created++;

                    } catch (Exception e) {
                        log.warn("Erro BOM linha {}: {}", bomRow.line() + 1, e.getMessage(), e);
                        errors.add(new ImportErrorDto(bomRow.line() + 1,
                                "Erro ao processar linha " + (bomRow.line() + 1)));
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Erro ao processar arquivo Excel de BOM: {}", e.getMessage(), e);
            errors.add(new ImportErrorDto(0,
                    "Erro ao processar o arquivo Excel. Verifique o formato e tente novamente."));
        }

        BomImportResponse response = buildResponse(total, created, updated, errors, username);

        auditService.log(username, AuditAction.PRODUCTION_IMPORT, "BomImport", "BOM",
                Map.of("created", created, "updated", updated, "errors", errors.size()));

        return response;
    }

    private BomImportResponse buildResponse(int total, int created, int updated,
                                             List<ImportErrorDto> errors, String username) {
        return new BomImportResponse(total, created, updated, errors.size(),
                username, LocalDateTime.now(), errors);
    }

    private record BomRow(int line, String parentCode, String componentCode,
                          Double quantity, String unit) {}
}
