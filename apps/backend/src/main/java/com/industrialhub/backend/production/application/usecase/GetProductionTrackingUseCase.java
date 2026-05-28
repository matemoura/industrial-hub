package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.ProductionTrackingColumnResponse;
import com.industrialhub.backend.production.application.dto.ProductionTrackingItemResponse;
import com.industrialhub.backend.production.application.dto.ProductionTrackingResponse;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Returns the kanban tracking view grouped by {@link ProductionOrderDisplayStatus}.
 * DisplayStatus is computed in Java — not stored in DB (ADR-041 Decisão 2).
 * Max 50 items per column; truncated flag set when exceeded (ADR-041 Decisão 4).
 */
@Service
public class GetProductionTrackingUseCase {

    private static final int MAX_PER_COLUMN = 50;

    /** Column order for the kanban board. CANCELLED / terminal statuses are excluded. */
    private static final List<ProductionOrderDisplayStatus> COLUMN_ORDER = List.of(
            ProductionOrderDisplayStatus.PLANNED,
            ProductionOrderDisplayStatus.IN_PROGRESS,
            ProductionOrderDisplayStatus.PENDING_QUALITY,
            ProductionOrderDisplayStatus.PENDING_STERILIZATION,
            ProductionOrderDisplayStatus.IN_LOAD,
            ProductionOrderDisplayStatus.FINISHED,
            ProductionOrderDisplayStatus.DONE
    );

    private final ProductionOrderRepository orderRepository;
    private final ImportProductionBatchRepository batchRepository;

    public GetProductionTrackingUseCase(ProductionOrderRepository orderRepository,
                                         ImportProductionBatchRepository batchRepository) {
        this.orderRepository = orderRepository;
        this.batchRepository = batchRepository;
    }

    public ProductionTrackingResponse execute(String familyCode, Boolean overdue) {
        LocalDate today = LocalDate.now();
        LocalDateTime weekStart = today
                .with(DayOfWeek.MONDAY)
                .atTime(LocalTime.MIDNIGHT);

        List<ProductionOrderTrackingView> raw = orderRepository.findForTracking(familyCode, weekStart);

        // Compute displayStatus and overdue for each row, then group
        Map<ProductionOrderDisplayStatus, List<ProductionTrackingItemResponse>> grouped = new EnumMap<>(
                ProductionOrderDisplayStatus.class);
        for (ProductionOrderDisplayStatus s : COLUMN_ORDER) {
            grouped.put(s, new ArrayList<>());
        }

        for (ProductionOrderTrackingView v : raw) {
            ProductionOrderDisplayStatus displayStatus = toDisplayStatus(v.getStatus());
            if (displayStatus == null) continue; // skip CANCELLED / unknown

            boolean isOverdue = v.getDueDate() != null
                    && v.getDueDate().isBefore(today)
                    && displayStatus != ProductionOrderDisplayStatus.DONE;

            // Apply overdue filter if requested
            if (Boolean.TRUE.equals(overdue) && !isOverdue) continue;

            ProductionTrackingItemResponse item = new ProductionTrackingItemResponse(
                    v.getId(),
                    v.getDynamicsOrderNumber(),
                    v.getProductName(),
                    v.getProductFamilyName(),
                    displayStatus,
                    v.getPlannedQty(),
                    v.getProducedQty(),
                    v.getDueDate(),
                    isOverdue
            );
            grouped.get(displayStatus).add(item);
        }

        LocalDateTime lastSyncAt = batchRepository
                .findFirstByTypeOrderByImportedAtDesc(ProductionImportType.PRODUCTION_ORDERS)
                .map(b -> b.getImportedAt())
                .orElse(null);

        List<ProductionTrackingColumnResponse> columns = COLUMN_ORDER.stream()
                .map(status -> {
                    List<ProductionTrackingItemResponse> items = grouped.get(status);
                    long total = items.size();
                    boolean truncated = total > MAX_PER_COLUMN;
                    List<ProductionTrackingItemResponse> limited = truncated
                            ? items.subList(0, MAX_PER_COLUMN)
                            : items;
                    return new ProductionTrackingColumnResponse(status, limited, truncated, total);
                })
                .collect(Collectors.toList());

        return new ProductionTrackingResponse(columns, lastSyncAt);
    }

    /**
     * Maps a raw {@link ProductionOrderStatus} to the visual {@link ProductionOrderDisplayStatus}.
     * Returns {@code null} for statuses that should be excluded from the kanban (CANCELLED).
     * ADR-041 Decisão 2 — Sprint 30 phase (no sterilization yet).
     */
    private ProductionOrderDisplayStatus toDisplayStatus(ProductionOrderStatus status) {
        if (status == null) return null;
        return switch (status) {
            case PLANNED    -> ProductionOrderDisplayStatus.PLANNED;
            case RELEASED   -> ProductionOrderDisplayStatus.PLANNED;   // no RELEASED column in this phase
            case IN_PROGRESS -> ProductionOrderDisplayStatus.IN_PROGRESS;
            case DONE       -> ProductionOrderDisplayStatus.DONE;
            case CANCELLED  -> null; // excluded
        };
    }
}
