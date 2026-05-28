package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.ProductionSummaryResponse;
import com.industrialhub.backend.production.domain.ProductionImportType;
import com.industrialhub.backend.production.domain.ProductionOrderDisplayStatus;
import com.industrialhub.backend.production.domain.ProductionOrderStatus;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderTrackingView;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Returns aggregate counts by display status for the production tracking dashboard header.
 * US-082 / ADR-041.
 */
@Service
public class GetProductionSummaryUseCase {

    private final ProductionOrderRepository orderRepository;
    private final ImportProductionBatchRepository batchRepository;

    public GetProductionSummaryUseCase(ProductionOrderRepository orderRepository,
                                        ImportProductionBatchRepository batchRepository) {
        this.orderRepository = orderRepository;
        this.batchRepository = batchRepository;
    }

    public ProductionSummaryResponse execute() {
        LocalDate today = LocalDate.now();
        LocalDateTime weekStart = today
                .with(DayOfWeek.MONDAY)
                .atTime(LocalTime.MIDNIGHT);

        List<ProductionOrderTrackingView> raw = orderRepository.findForTracking(null, weekStart);

        Map<String, Long> countByStatus = new LinkedHashMap<>();
        long totalActive = 0;

        for (ProductionOrderTrackingView v : raw) {
            ProductionOrderDisplayStatus ds = toDisplayStatus(v.getStatus());
            if (ds == null) continue;
            String key = ds.name();
            countByStatus.merge(key, 1L, Long::sum);
            if (ds != ProductionOrderDisplayStatus.DONE) {
                totalActive++;
            }
        }

        LocalDateTime lastSyncAt = batchRepository
                .findFirstByTypeOrderByImportedAtDesc(ProductionImportType.PRODUCTION_ORDERS)
                .map(b -> b.getImportedAt())
                .orElse(null);

        return new ProductionSummaryResponse(countByStatus, totalActive, lastSyncAt);
    }

    private ProductionOrderDisplayStatus toDisplayStatus(ProductionOrderStatus status) {
        if (status == null) return null;
        return switch (status) {
            case PLANNED    -> ProductionOrderDisplayStatus.PLANNED;
            case RELEASED   -> ProductionOrderDisplayStatus.PLANNED;
            case IN_PROGRESS -> ProductionOrderDisplayStatus.IN_PROGRESS;
            case DONE       -> ProductionOrderDisplayStatus.DONE;
            case CANCELLED  -> null;
        };
    }
}
