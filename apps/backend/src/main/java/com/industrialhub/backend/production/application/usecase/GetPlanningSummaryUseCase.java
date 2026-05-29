package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.PlanningSummaryRow;
import com.industrialhub.backend.production.infrastructure.MrpPlannedOrderRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import com.industrialhub.backend.production.domain.MrpOrderStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ADR-044 Decisões 5 e 6 — relatório planned vs actual + exportação CSV.
 * efficiency calculada em Java (não em JPQL) — evita divisão por zero.
 * pendingMrpQty injetado via MrpPlannedOrderRepository (não em JPQL para evitar subconsulta correlated complexa).
 */
@Service
public class GetPlanningSummaryUseCase {

    private final ProductionOrderRepository orderRepository;
    private final MrpPlannedOrderRepository mrpRepository;

    public GetPlanningSummaryUseCase(ProductionOrderRepository orderRepository,
                                      MrpPlannedOrderRepository mrpRepository) {
        this.orderRepository = orderRepository;
        this.mrpRepository = mrpRepository;
    }

    public List<PlanningSummaryRow> getSummary(String familyCode, LocalDate from, LocalDate to) {
        // 1. Aggregation query (plannedQty, producedQty por produto/família no período)
        List<PlanningSummaryRow> raw = orderRepository.findPlanningSummaryRaw(familyCode, from, to);

        // 2. Pending MRP suggestions por produto (SUGGESTED + ACCEPTED)
        Map<String, Integer> pendingByProduct = mrpRepository.findAll().stream()
                .filter(m -> m.getStatus() == MrpOrderStatus.SUGGESTED
                          || m.getStatus() == MrpOrderStatus.ACCEPTED)
                .collect(Collectors.groupingBy(
                        m -> m.getProduct().getDynamicsCode(),
                        Collectors.summingInt(m -> m.getAdjustedQty() != null
                                ? m.getAdjustedQty() : m.getSuggestedQty())
                ));

        // 3. Enrich rows: calculate efficiency + pendingMrpQty
        return raw.stream().map(r -> {
            Double efficiency = r.plannedQty() != null && r.plannedQty() > 0
                    ? r.producedQty() * 100.0 / r.plannedQty()
                    : null;
            int pending = pendingByProduct.getOrDefault(r.productCode(), 0);
            return new PlanningSummaryRow(
                    r.familyCode(), r.familyName(),
                    r.productCode(), r.productName(),
                    r.plannedQty(), r.producedQty(),
                    efficiency, pending
            );
        }).toList();
    }

    /**
     * ADR-044 Decisão 6 — exportação CSV com UTF-8 BOM para compatibilidade com Excel pt-BR.
     */
    public ResponseEntity<byte[]> exportCsv(String familyCode, LocalDate from, LocalDate to) {
        List<PlanningSummaryRow> rows = getSummary(familyCode, from, to);

        StringBuilder sb = new StringBuilder();
        sb.append('﻿'); // UTF-8 BOM — necessário para Excel pt-BR reconhecer separador ';'
        sb.append("Família;Produto;Código;Qtd Planejada;Qtd Produzida;Eficiência (%);Sugestões MRP Pendentes\n");

        for (PlanningSummaryRow row : rows) {
            sb.append(escapeCsv(row.familyName())).append(';')
              .append(escapeCsv(row.productName())).append(';')
              .append(row.productCode()).append(';')
              .append(row.plannedQty() != null ? row.plannedQty() : 0).append(';')
              .append(row.producedQty() != null ? row.producedQty() : 0).append(';')
              .append(row.efficiency() != null
                      ? String.format("%.1f", row.efficiency()).replace('.', ',')
                      : "").append(';')
              .append(row.pendingMrpQty() != null ? row.pendingMrpQty() : 0)
              .append('\n');
        }

        byte[] csvBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "planejamento-" + from + "-" + to + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvBytes);
    }

    // SEC-119: prefixos de fórmula que o Excel interpreta ao abrir CSV
    private static final java.util.Set<Character> FORMULA_PREFIXES = java.util.Set.of('=', '+', '-', '@');

    private String escapeCsv(String value) {
        if (value == null) return "";
        // SEC-119: neutraliza CSV formula injection — tab antes do conteúdo é inerte no Excel
        if (!value.isEmpty() && FORMULA_PREFIXES.contains(value.charAt(0))) {
            value = "\t" + value;
        }
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
