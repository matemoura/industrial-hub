package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.production.application.dto.ImportErrorDto;
import com.industrialhub.backend.production.application.dto.ImportProductionBatchResponse;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import com.industrialhub.backend.production.infrastructure.ProductRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ImportProductionOrdersUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImportProductionOrdersUseCase.class);

    private final ProductRepository productRepository;
    private final ProductionOrderRepository orderRepository;
    private final ImportProductionBatchRepository batchRepository;
    private final AuditService auditService;

    public ImportProductionOrdersUseCase(ProductRepository productRepository,
                                          ProductionOrderRepository orderRepository,
                                          ImportProductionBatchRepository batchRepository,
                                          AuditService auditService) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
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
                return saveBatch(fileName, username, total, created, updated, errors);
            }

            Map<String, Integer> colIndex = ExcelParsingHelper.buildColumnIndex(header);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                total++;

                try {
                    String orderNumber = ExcelParsingHelper.getString(row, colIndex, "op_number");
                    if (orderNumber == null || orderNumber.isBlank()) {
                        errors.add(new ImportErrorDto(i + 1, "op_number ausente"));
                        continue;
                    }

                    String dynamicsCode = ExcelParsingHelper.getString(row, colIndex, "dynamics_code");
                    if (dynamicsCode == null || dynamicsCode.isBlank()) {
                        errors.add(new ImportErrorDto(i + 1, "dynamics_code ausente"));
                        continue;
                    }

                    String statusStr = ExcelParsingHelper.getString(row, colIndex, "status");
                    ProductionOrderStatus status;
                    try {
                        status = ProductionOrderStatus.valueOf(statusStr != null ? statusStr.trim().toUpperCase() : "");
                    } catch (IllegalArgumentException e) {
                        errors.add(new ImportErrorDto(i + 1, "status inválido: " + statusStr));
                        continue;
                    }

                    Optional<Product> productOpt = productRepository.findByDynamicsCode(dynamicsCode.trim());
                    if (productOpt.isEmpty()) {
                        errors.add(new ImportErrorDto(i + 1, "Produto não encontrado: " + dynamicsCode));
                        continue;
                    }
                    Product product = productOpt.get();

                    Double plannedQtyD = ExcelParsingHelper.getDouble(row, colIndex, "planned_qty");
                    Double producedQtyD = ExcelParsingHelper.getDouble(row, colIndex, "produced_qty");
                    LocalDate startDate = ExcelParsingHelper.getLocalDate(row, colIndex, "start_date");
                    LocalDate dueDate = ExcelParsingHelper.getLocalDate(row, colIndex, "due_date");

                    BigDecimal plannedQty = plannedQtyD != null ? BigDecimal.valueOf(plannedQtyD) : BigDecimal.ZERO;
                    BigDecimal producedQty = producedQtyD != null ? BigDecimal.valueOf(producedQtyD) : null;

                    Optional<ProductionOrder> existing = orderRepository.findByDynamicsOrderNumber(orderNumber.trim());
                    if (existing.isPresent()) {
                        ProductionOrder order = existing.get();
                        // Update Dynamics-managed fields; preserve Hub-managed fields
                        order.setStatus(status);
                        order.setPlannedQty(plannedQty);
                        order.setProducedQty(producedQty);
                        order.setStartDate(startDate);
                        order.setDueDate(dueDate);
                        order.setUpdatedAt(LocalDateTime.now());
                        // Do NOT touch: plannedPeople, peopleOverridden, sterilizationLoad (Hub-managed)
                        orderRepository.save(order);
                        updated++;
                    } else {
                        ProductionOrder order = ProductionOrder.builder()
                                .dynamicsOrderNumber(orderNumber.trim())
                                .product(product)
                                .family(product.getFamily())
                                .status(status)
                                .plannedQty(plannedQty)
                                .producedQty(producedQty)
                                .startDate(startDate)
                                .dueDate(dueDate)
                                .importedAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        orderRepository.save(order);
                        created++;
                    }
                } catch (Exception e) {
                    // SEC-108: sanitize unexpected exceptions — never expose stack trace or DB messages
                    log.warn("Erro linha {}: {}", i + 1, e.getMessage());
                    errors.add(new ImportErrorDto(i + 1, "Erro ao processar linha %d".formatted(i + 1)));
                }
            }
        } catch (IOException e) {
            errors.add(new ImportErrorDto(0, "Erro ao ler o arquivo Excel: " + e.getMessage()));
        }

        return saveBatch(fileName, username, total, created, updated, errors);
    }

    private ImportProductionBatchResponse saveBatch(String fileName, String username,
                                                      int total, int created, int updated,
                                                      List<ImportErrorDto> errors) {
        ImportProductionBatch batch = batchRepository.save(ImportProductionBatch.builder()
                .type(ProductionImportType.PRODUCTION_ORDERS)
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
                Map.of("type", "PRODUCTION_ORDERS", "created", created, "updated", updated, "errors", errors.size()));

        return ImportProductionBatchResponse.from(batch, errors);
    }
}
