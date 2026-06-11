package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.production.application.dto.ImportErrorDto;
import com.industrialhub.backend.production.application.dto.ImportProductionBatchResponse;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.CycleTimeRepository;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import com.industrialhub.backend.production.infrastructure.ProductRepository;
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
import java.util.stream.Collectors;

/**
 * Importa tempos de ciclo a partir do extrato de apontamentos do Dynamics.
 *
 * Lógica de agregação:
 *   1. Lê linhas do tipo "Hora" e "Quantidade" agrupadas por (nº da OP, mês).
 *   2. Resolve a OP para o produto via ProductionOrderRepository.
 *   3. Agrega por (produto, mês) — soma horas e quantidade de todas as OPs do produto naquele mês.
 *   4. seconds_per_unit = (totalHoras / totalQtd) × 3600.
 *   5. effective_date = 1º dia do mês.
 */
@Service
public class ImportCycleTimesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImportCycleTimesUseCase.class);

    private static final String COL_OP     = "número";
    private static final String COL_TIPO   = "tipo";
    private static final String COL_VALOR  = "quantidade/tempo";
    private static final String COL_DATA   = "data_física";

    private final ProductRepository productRepository;
    private final CycleTimeRepository cycleTimeRepository;
    private final ImportProductionBatchRepository batchRepository;
    private final ProductionOrderRepository orderRepository;
    private final AuditService auditService;

    public ImportCycleTimesUseCase(ProductRepository productRepository,
                                   CycleTimeRepository cycleTimeRepository,
                                   ImportProductionBatchRepository batchRepository,
                                   ProductionOrderRepository orderRepository,
                                   AuditService auditService) {
        this.productRepository  = productRepository;
        this.cycleTimeRepository = cycleTimeRepository;
        this.batchRepository    = batchRepository;
        this.orderRepository    = orderRepository;
        this.auditService       = auditService;
    }

    @Transactional
    public ImportProductionBatchResponse execute(MultipartFile file, String username) {
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
        int created = 0, updated = 0, total = 0;

        // ── Fase 1: acumular horas e quantidade por chave "opNumber::YYYY-MM" ──────
        // double[0] = totalHoras, double[1] = totalQtd
        Map<String, double[]> accByOpMonth    = new LinkedHashMap<>();
        Map<String, LocalDate> dateByOpMonth  = new LinkedHashMap<>();
        Map<String, String>    opByKey        = new LinkedHashMap<>();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet  = wb.getSheetAt(0);
            Row   header = sheet.getRow(0);

            if (header == null) {
                errors.add(new ImportErrorDto(0, "Planilha vazia ou sem cabeçalho"));
                return saveBatch(fileName, username, 0, 0, 0, errors);
            }

            Map<String, Integer> colIndex = ExcelParsingHelper.buildColumnIndex(header);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                total++;

                try {
                    String    opNumber  = ExcelParsingHelper.getString   (row, colIndex, COL_OP);
                    String    tipo      = ExcelParsingHelper.getString   (row, colIndex, COL_TIPO);
                    Double    valor     = ExcelParsingHelper.getDouble   (row, colIndex, COL_VALOR);
                    LocalDate dataFisica = ExcelParsingHelper.getLocalDate(row, colIndex, COL_DATA);

                    if (opNumber == null || tipo == null || valor == null || dataFisica == null) continue;
                    if (valor <= 0) continue;

                    String yearMonth = "%d-%02d".formatted(dataFisica.getYear(), dataFisica.getMonthValue());
                    String key       = opNumber.trim() + "::" + yearMonth;

                    accByOpMonth.computeIfAbsent(key, k -> new double[]{0.0, 0.0});
                    dateByOpMonth.putIfAbsent(key, dataFisica.withDayOfMonth(1));
                    opByKey.putIfAbsent(key, opNumber.trim());

                    String tipoNorm = tipo.trim().toLowerCase(Locale.ROOT);
                    if ("hora".equals(tipoNorm)) {
                        accByOpMonth.get(key)[0] += valor;
                    } else if ("quantidade".equals(tipoNorm)) {
                        accByOpMonth.get(key)[1] += valor;
                    }
                } catch (Exception e) {
                    log.warn("Erro linha {}: {}", i + 1, e.getMessage(), e);
                    errors.add(new ImportErrorDto(i + 1, "Erro ao processar linha %d".formatted(i + 1)));
                }
            }
        } catch (IOException e) {
            log.warn("Erro ao processar arquivo Excel na importação de cycle times: {}", e.getMessage(), e);
            errors.add(new ImportErrorDto(0, "Erro ao processar o arquivo Excel. Verifique o formato e tente novamente."));
            return saveBatch(fileName, username, total, 0, 0, errors);
        }

        // ── Fase 2: batch-load todas as OPs de uma vez, depois agregar por (produto, mês) ──
        Set<String> allOpNumbers = new HashSet<>(opByKey.values());
        Map<String, ProductionOrder> orderMap = orderRepository
                .findByDynamicsOrderNumberIn(allOpNumbers)
                .stream()
                .collect(Collectors.toMap(ProductionOrder::getDynamicsOrderNumber, o -> o));

        // key: "productId::YYYY-MM"
        Map<String, double[]>    productAcc  = new LinkedHashMap<>();
        Map<String, LocalDate>   productDate = new LinkedHashMap<>();
        Map<String, Product>     productMap  = new LinkedHashMap<>();

        for (Map.Entry<String, double[]> entry : accByOpMonth.entrySet()) {
            String    key      = entry.getKey();
            double[]  vals     = entry.getValue();
            String    opNumber = opByKey.get(key);
            String    month    = key.substring(key.indexOf("::") + 2);

            if (vals[1] <= 0) continue; // sem quantidade apontada, não calcula

            ProductionOrder order = orderMap.get(opNumber);
            if (order == null) {
                errors.add(new ImportErrorDto(0, "Ordem não encontrada: " + opNumber));
                continue;
            }

            Product product    = order.getProduct();
            String  productKey = product.getId() + "::" + month;

            productAcc.computeIfAbsent(productKey, k -> new double[]{0.0, 0.0});
            productDate.putIfAbsent(productKey, dateByOpMonth.get(key));
            productMap.putIfAbsent(productKey, product);

            productAcc.get(productKey)[0] += vals[0]; // acumula horas
            productAcc.get(productKey)[1] += vals[1]; // acumula quantidade
        }

        // ── Fase 3: calcular segundos/unidade e persistir ────────────────────────
        for (Map.Entry<String, double[]> entry : productAcc.entrySet()) {
            String   productKey   = entry.getKey();
            double[] vals         = entry.getValue();
            if (vals[1] <= 0) continue;

            double    secondsPerUnit = (vals[0] / vals[1]) * 3600.0;
            LocalDate effectiveDate  = productDate.get(productKey);
            Product   product        = productMap.get(productKey);

            try {
                Optional<CycleTime> existing = cycleTimeRepository
                        .findByProductIdAndEffectiveDate(product.getId(), effectiveDate);

                if (existing.isPresent()) {
                    CycleTime ct = existing.get();
                    ct.setSecondsPerUnit(secondsPerUnit);
                    ct.setImportedAt(LocalDateTime.now());
                    ct.setImportedBy(username);
                    cycleTimeRepository.save(ct);
                    updated++;
                } else {
                    cycleTimeRepository.save(CycleTime.builder()
                            .product(product)
                            .secondsPerUnit(secondsPerUnit)
                            .effectiveDate(effectiveDate)
                            .importedBy(username)
                            .importedAt(LocalDateTime.now())
                            .build());
                    created++;
                }
            } catch (Exception e) {
                log.warn("Erro ao salvar cycle time para {}: {}", product.getDynamicsCode(), e.getMessage(), e);
                errors.add(new ImportErrorDto(0, "Erro ao salvar cycle time: " + product.getDynamicsCode()));
            }
        }

        return saveBatch(fileName, username, total, created, updated, errors);
    }

    private ImportProductionBatchResponse saveBatch(String fileName, String username,
                                                    int total, int created, int updated,
                                                    List<ImportErrorDto> errors) {
        ImportProductionBatch batch = batchRepository.save(ImportProductionBatch.builder()
                .type(ProductionImportType.CYCLE_TIMES)
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
                Map.of("type", "CYCLE_TIMES", "created", created, "updated", updated, "errors", errors.size()));

        return ImportProductionBatchResponse.from(batch, errors);
    }
}
