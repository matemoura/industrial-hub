package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.DailyEfficiencyDto;
import com.industrialhub.backend.production.application.dto.ProductionOverviewDto;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * US-104 / ADR-045 Decisão 1 — agrega KPIs do painel executivo de produção.
 * Sem cache por ora (TTL 5 min via Spring Cache Caffeine pode ser adicionado em Sprint 35
 * após configurar CaffeineCacheManager no projeto).
 */
@Service
public class GetProductionOverviewUseCase {

    private final ProductRepository productRepository;
    private final ProductComponentRepository componentRepository;
    private final MrpPlannedOrderRepository mrpRepository;
    private final ProductionOrderRepository orderRepository;

    public GetProductionOverviewUseCase(ProductRepository productRepository,
                                         ProductComponentRepository componentRepository,
                                         MrpPlannedOrderRepository mrpRepository,
                                         ProductionOrderRepository orderRepository) {
        this.productRepository  = productRepository;
        this.componentRepository = componentRepository;
        this.mrpRepository      = mrpRepository;
        this.orderRepository    = orderRepository;
    }

    @Cacheable(value = "production-overview", key = "'overview'")
    public ProductionOverviewDto getOverview() {
        LocalDate today = LocalDate.now();
        LocalDate from  = today.minusDays(30);

        // ── BOM coverage ──────────────────────────────────────────────────────────
        List<Product> finished = productRepository.findAll().stream()
                .filter(p -> p.getType() == ProductType.FINISHED && p.isActive())
                .toList();

        Set<String> codesWithBom = componentRepository.findAllActive().stream()
                .map(c -> c.getParentProduct().getDynamicsCode())
                .collect(Collectors.toSet());

        int withBom       = (int) finished.stream()
                .filter(p -> codesWithBom.contains(p.getDynamicsCode()))
                .count();
        int totalFinished = finished.size();
        Double coveragePct = totalFinished > 0 ? withBom * 100.0 / totalFinished : null;

        // ── MRP fulfillment ───────────────────────────────────────────────────────
        List<MrpPlannedOrder> allMrp = mrpRepository.findAll();
        int accepted = (int) allMrp.stream().filter(m -> m.getStatus() == MrpOrderStatus.ACCEPTED).count();
        int rejected = (int) allMrp.stream().filter(m -> m.getStatus() == MrpOrderStatus.REJECTED).count();
        int pending  = (int) allMrp.stream().filter(m -> m.getStatus() == MrpOrderStatus.SUGGESTED).count();
        int eligible = allMrp.size() - rejected;
        Double fulfillmentPct = eligible > 0 ? accepted * 100.0 / eligible : null;

        // ── Efficiency trend (últimos 30 dias — DONE orders agrupadas por dueDate) ──
        List<ProductionOrder> doneOrders = orderRepository.findDoneOrdersInPeriod(from, today);
        List<DailyEfficiencyDto> trend = doneOrders.stream()
                .filter(o -> o.getPlannedQty() != null
                          && o.getPlannedQty().compareTo(BigDecimal.ZERO) > 0
                          && o.getDueDate() != null)
                .collect(Collectors.groupingBy(
                        ProductionOrder::getDueDate,
                        Collectors.averagingDouble(o ->
                                o.getProducedQty() != null
                                        ? o.getProducedQty().doubleValue() * 100.0
                                          / o.getPlannedQty().doubleValue()
                                        : 0.0
                        )
                ))
                .entrySet().stream()
                .map(e -> new DailyEfficiencyDto(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(DailyEfficiencyDto::date))
                .toList();

        // ── OPs por status ────────────────────────────────────────────────────────
        Map<String, Long> opsByStatus = orderRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        o -> o.getStatus().name(),
                        Collectors.counting()
                ));

        return new ProductionOverviewDto(
                new ProductionOverviewDto.BomCoverageDto(
                        totalFinished, withBom, totalFinished - withBom, coveragePct),
                new ProductionOverviewDto.MrpFulfillmentDto(
                        allMrp.size(), accepted, rejected, pending, fulfillmentPct),
                trend,
                opsByStatus
        );
    }
}
