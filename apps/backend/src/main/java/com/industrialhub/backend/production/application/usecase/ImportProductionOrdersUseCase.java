package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.production.application.dto.ImportErrorDto;
import com.industrialhub.backend.production.application.dto.ImportProductionBatchResponse;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.CycleTimeRepository;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import com.industrialhub.backend.production.infrastructure.ProductRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import com.industrialhub.backend.production.infrastructure.SterilizationLoadRepository;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.industrialhub.backend.production.application.util.BusinessDaysCalculator;
import com.industrialhub.backend.production.domain.StaffingConfig;

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
    private final CycleTimeRepository cycleTimeRepository;
    private final GetStaffingConfigUseCase getStaffingConfig;
    private final SterilizationLoadRepository loadRepository;

    public ImportProductionOrdersUseCase(ProductRepository productRepository,
                                          ProductionOrderRepository orderRepository,
                                          ImportProductionBatchRepository batchRepository,
                                          AuditService auditService,
                                          CycleTimeRepository cycleTimeRepository,
                                          GetStaffingConfigUseCase getStaffingConfig,
                                          SterilizationLoadRepository loadRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.batchRepository = batchRepository;
        this.auditService = auditService;
        this.cycleTimeRepository = cycleTimeRepository;
        this.getStaffingConfig = getStaffingConfig;
        this.loadRepository = loadRepository;
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

            // Accept internal column names OR Dynamics Portuguese export column names
            String headerError = ExcelParsingHelper.validateRequiredColumnsAliased(colIndex,
                    new String[]{"op_number", "produção"},
                    new String[]{"dynamics_code", "número_do_item"},
                    new String[]{"planned_qty", "quantidade"});
            if (headerError != null) {
                errors.add(new ImportErrorDto(1, headerError));
                return saveBatch(fileName, username, total, created, updated, errors);
            }

            // SEC-117: cache StaffingConfig once before the loop — avoids N queries for each OP row
            StaffingConfig staffingConfig = getStaffingConfig.getOrCreate();

            // Pre-load all existing products, orders and sterilization loads — avoids N+1 queries
            Map<String, Product>           existingProducts = new java.util.HashMap<>();
            Map<String, ProductionOrder>   existingOrders   = new java.util.HashMap<>();
            Map<String, SterilizationLoad> loadsBySequencia = new java.util.HashMap<>();
            productRepository.findAll().forEach(p -> existingProducts.put(p.getDynamicsCode(), p));
            orderRepository.findAll().forEach(o -> existingOrders.put(o.getDynamicsOrderNumber(), o));
            loadRepository.findAll().forEach(l -> {
                if (l.getBatchCode() != null && !l.getBatchCode().isBlank()) {
                    loadsBySequencia.put(l.getBatchCode().trim(), l);
                }
            });

            int importYear = LocalDate.now().getYear();
            // Track next sequence locally to avoid repeated DB queries within the same transaction
            int[] nextSeq = { loadRepository.nextSequenceForYear(importYear) };

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                total++;

                try {
                    String orderNumber = ExcelParsingHelper.getStringByAliases(row, colIndex,
                            "op_number", "produção");
                    if (orderNumber == null || orderNumber.isBlank()) {
                        errors.add(new ImportErrorDto(i + 1, "Número da ordem ausente"));
                        continue;
                    }

                    String dynamicsCode = ExcelParsingHelper.getStringByAliases(row, colIndex,
                            "dynamics_code", "número_do_item");
                    if (dynamicsCode == null || dynamicsCode.isBlank()) {
                        errors.add(new ImportErrorDto(i + 1, "Código do item ausente"));
                        continue;
                    }

                    String statusStr = ExcelParsingHelper.getString(row, colIndex, "status");
                    ProductionOrderStatus status = parseOrderStatus(statusStr);

                    Product product = existingProducts.get(dynamicsCode.trim());
                    if (product == null) {
                        errors.add(new ImportErrorDto(i + 1, "Produto não encontrado: " + dynamicsCode));
                        continue;
                    }

                    Double plannedQtyD = ExcelParsingHelper.getDoubleByAliases(row, colIndex,
                            "planned_qty", "quantidade");
                    Double producedQtyD = ExcelParsingHelper.getDouble(row, colIndex, "produced_qty");
                    LocalDate startDate = ExcelParsingHelper.getLocalDateByAliases(row, colIndex,
                            "start_date", "início");
                    LocalDate dueDate = ExcelParsingHelper.getLocalDateByAliases(row, colIndex,
                            "due_date", "entrega");

                    BigDecimal plannedQty = plannedQtyD != null ? BigDecimal.valueOf(plannedQtyD) : BigDecimal.ZERO;
                    BigDecimal producedQty = producedQtyD != null ? BigDecimal.valueOf(producedQtyD) : null;

                    // Resolve sterilization load from the Sequencia column (Dynamics carga/família grouping)
                    String sequencia = ExcelParsingHelper.getStringByAliases(row, colIndex,
                            "sequencia", "sequência");
                    SterilizationLoad resolvedLoad = resolveLoad(sequencia, loadsBySequencia, importYear, nextSeq, username);

                    ProductionOrder existingOrder = existingOrders.get(orderNumber.trim());
                    if (existingOrder != null) {
                        // Update Dynamics-managed fields; preserve Hub-managed fields
                        existingOrder.setStatus(status);
                        existingOrder.setPlannedQty(plannedQty);
                        existingOrder.setProducedQty(producedQty);
                        existingOrder.setStartDate(startDate);
                        existingOrder.setDueDate(dueDate);
                        existingOrder.setUpdatedAt(LocalDateTime.now());
                        // US-086 AC#2 — recalculate staffing on import if not manually overridden
                        if (!existingOrder.isPeopleOverridden()) {
                            existingOrder.setPlannedPeople(calculateStaffing(product, plannedQty, dueDate, staffingConfig));
                        }
                        // Sequencia drives sterilization load; blank Sequencia preserves existing Hub assignment
                        if (resolvedLoad != null) {
                            existingOrder.setSterilizationLoad(resolvedLoad);
                        }
                        orderRepository.save(existingOrder);
                        updated++;
                    } else {
                        // US-086 AC#2 — calculate staffing for new OPs on import
                        Integer staffing = calculateStaffing(product, plannedQty, dueDate, staffingConfig);
                        ProductionOrder order = ProductionOrder.builder()
                                .dynamicsOrderNumber(orderNumber.trim())
                                .product(product)
                                .family(product.getFamily())
                                .status(status)
                                .plannedQty(plannedQty)
                                .producedQty(producedQty)
                                .startDate(startDate)
                                .dueDate(dueDate)
                                .plannedPeople(staffing)
                                .sterilizationLoad(resolvedLoad)
                                .importedAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        order = orderRepository.save(order);
                        existingOrders.put(orderNumber.trim(), order);
                        created++;
                    }
                } catch (Exception e) {
                    // SEC-108: sanitize unexpected exceptions — never expose stack trace or DB messages
                    log.warn("Erro linha {}: {}", i + 1, e.getMessage(), e);
                    errors.add(new ImportErrorDto(i + 1, "Erro ao processar linha %d".formatted(i + 1)));
                }
            }
        } catch (IOException e) {
            log.warn("Erro ao processar arquivo Excel na importação de ordens de produção: {}", e.getMessage(), e);
            errors.add(new ImportErrorDto(0, "Erro ao processar o arquivo Excel. Verifique o formato e tente novamente."));
        }

        return saveBatch(fileName, username, total, created, updated, errors);
    }

    /** Translates Dynamics order status values (Portuguese or English) to the internal enum. */
    private ProductionOrderStatus parseOrderStatus(String s) {
        if (s == null) return ProductionOrderStatus.PLANNED;
        try {
            return ProductionOrderStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {}
        String norm = s.trim().toLowerCase();
        if (norm.contains("processo") || norm.contains("progress") || norm.contains("iniciada")) return ProductionOrderStatus.IN_PROGRESS;
        if (norm.contains("liberada") || norm.contains("released")) return ProductionOrderStatus.RELEASED;
        if (norm.contains("conclu") || norm.contains("finaliz") || norm.contains("encerr") || norm.contains("done") || norm.contains("completed")) return ProductionOrderStatus.DONE;
        if (norm.contains("cancel")) return ProductionOrderStatus.CANCELLED;
        return ProductionOrderStatus.PLANNED;
    }

    /**
     * US-086 AC#2 — cálculo automático de staffing na importação.
     * Reutiliza a mesma lógica de ResetOrderStaffingUseCase.
     * Retorna null se não há CycleTime ou plannedQty.
     * SEC-117: recebe StaffingConfig como parâmetro (pré-carregado antes do loop).
     */
    private Integer calculateStaffing(Product product, BigDecimal plannedQty, LocalDate dueDate,
                                      StaffingConfig config) {
        if (plannedQty == null) return null;
        var cycleTimeOpt = cycleTimeRepository.findTopByProductIdOrderByEffectiveDateDesc(product.getId());
        if (cycleTimeOpt.isEmpty()) return null;

        double secondsPerUnit = cycleTimeOpt.get().getSecondsPerUnit();
        int workdaySeconds = config.getShiftHours() * config.getShiftsPerDay() * 3600;
        int workdays = dueDate != null
                ? BusinessDaysCalculator.workdaysUntil(LocalDate.now(), dueDate)
                : 1;

        double totalSeconds = plannedQty.doubleValue() * secondsPerUnit;
        return (int) Math.ceil(totalSeconds / ((double) workdaySeconds * workdays));
    }

    /**
     * Finds an existing SterilizationLoad by batchCode (= Sequencia) or creates a new one.
     * Returns null when sequencia is blank — preserves Hub-managed assignment on existing orders.
     */
    private SterilizationLoad resolveLoad(String sequencia,
                                          Map<String, SterilizationLoad> cache,
                                          int year, int[] nextSeq, String username) {
        if (sequencia == null || sequencia.isBlank()) return null;
        String key = sequencia.trim();
        return cache.computeIfAbsent(key, k -> {
            String loadNumber = "CARGA-%d-%03d".formatted(year, nextSeq[0]++);
            SterilizationLoad load = SterilizationLoad.builder()
                    .loadNumber(loadNumber)
                    .batchCode(k)
                    .status(LoadStatus.OPEN)
                    .method(SterilizationMethod.OTHER)
                    .createdBy(username)
                    .createdAt(LocalDateTime.now())
                    .build();
            return loadRepository.save(load);
        });
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
