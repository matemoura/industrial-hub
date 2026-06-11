package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.FamilyPlanningBoardResponse;
import com.industrialhub.backend.production.application.dto.FamilyPlanningBoardResponse.PlanningStatus;
import com.industrialhub.backend.production.application.dto.FamilyPlanningBoardResponse.ProductPlanningRow;
import com.industrialhub.backend.production.domain.Product;
import com.industrialhub.backend.production.domain.ProductFamily;
import com.industrialhub.backend.production.domain.ProductType;
import com.industrialhub.backend.production.infrastructure.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** ADR-030 Decisão 6 / ADR-043 — board calculado em real-time (sem snapshot) */
@Service
public class GetPlanningBoardUseCase {

    private final ProductRepository productRepository;
    private final StockSnapshotRepository stockSnapshotRepository;
    private final ProductionOrderRepository productionOrderRepository;
    private final MrpPlannedOrderRepository mrpPlannedOrderRepository;
    private final ProductFamilyRepository productFamilyRepository;

    public GetPlanningBoardUseCase(
            ProductRepository productRepository,
            StockSnapshotRepository stockSnapshotRepository,
            ProductionOrderRepository productionOrderRepository,
            MrpPlannedOrderRepository mrpPlannedOrderRepository,
            ProductFamilyRepository productFamilyRepository) {
        this.productRepository = productRepository;
        this.stockSnapshotRepository = stockSnapshotRepository;
        this.productionOrderRepository = productionOrderRepository;
        this.mrpPlannedOrderRepository = mrpPlannedOrderRepository;
        this.productFamilyRepository = productFamilyRepository;
    }

    @Transactional(readOnly = true)
    public List<FamilyPlanningBoardResponse> execute() {
        // Estoque mais recente por produto (uma query para todos)
        Map<UUID, com.industrialhub.backend.production.domain.StockSnapshot> latestStock =
                stockSnapshotRepository.findLatestPerProduct().stream()
                        .collect(Collectors.toMap(
                                s -> s.getProduct().getId(),
                                s -> s,
                                (a, b) -> a));

        // Todos produtos FINISHED ativos
        List<Product> products = productRepository.findAll().stream()
                .filter(p -> p.getType() == ProductType.FINISHED && p.isActive())
                .toList();

        // Agrupar por família
        Map<ProductFamily, List<Product>> byFamily = products.stream()
                .filter(p -> p.getFamily() != null)
                .collect(Collectors.groupingBy(Product::getFamily));

        List<FamilyPlanningBoardResponse> result = new ArrayList<>();
        for (Map.Entry<ProductFamily, List<Product>> entry : byFamily.entrySet()) {
            ProductFamily family = entry.getKey();
            List<ProductPlanningRow> rows = entry.getValue().stream()
                    .map(p -> buildRow(p, latestStock.get(p.getId())))
                    .toList();
            if (!rows.isEmpty()) {
                result.add(new FamilyPlanningBoardResponse(
                        family.getId(), family.getCode(), family.getName(), rows));
            }
        }
        return result;
    }

    private ProductPlanningRow buildRow(Product product,
            com.industrialhub.backend.production.domain.StockSnapshot stock) {

        int currentStock = stock != null && stock.getQty() != null ? stock.getQty() : 0;
        LocalDate stockDate = stock != null ? stock.getSnapshotDate() : null;
        int minStock = product.getMinStockQty() != null ? product.getMinStockQty() : 0;

        int openOrdersQty = productionOrderRepository.sumOpenOrdersQtyForBoardByProduct(product.getId());
        int suggestedQty = mrpPlannedOrderRepository.sumActiveSuggestedQtyByProduct(product.getId());
        int netNeed = Math.max(0, minStock - currentStock - openOrdersQty - suggestedQty);

        int totalPeople = productionOrderRepository.sumPlannedPeopleByProduct(product.getId());

        java.util.List<Object[]> countAndDateResult = productionOrderRepository.countAndEarliestDueDateByProduct(product.getId());
        Object[] countAndDate = (countAndDateResult != null && !countAndDateResult.isEmpty()) ? countAndDateResult.get(0) : null;
        int totalOpsOpen = countAndDate != null && countAndDate[0] != null
                ? ((Number) countAndDate[0]).intValue() : 0;
        LocalDate earliestDue = countAndDate != null && countAndDate[1] != null
                ? (LocalDate) countAndDate[1] : null;

        PlanningStatus status = computeStatus(netNeed, minStock);

        return new ProductPlanningRow(
                product.getDynamicsCode(),
                product.getName(),
                product.getType(),
                currentStock,
                minStock,
                stockDate,
                openOrdersQty,
                suggestedQty,
                netNeed,
                status,
                totalPeople,
                totalOpsOpen,
                product.getLeadTimeDays(),
                earliestDue
        );
    }

    private PlanningStatus computeStatus(int netNeed, int minStock) {
        if (netNeed == 0) return PlanningStatus.OK;
        if (minStock > 0 && netNeed > minStock / 2.0) return PlanningStatus.CRITICAL;
        return PlanningStatus.ALERT;
    }
}
