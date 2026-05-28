package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.ProductionOrderListResponse;
import com.industrialhub.backend.production.application.dto.ProductionOrderTrackingItemResponse;
import com.industrialhub.backend.production.domain.ProductionImportType;
import com.industrialhub.backend.production.domain.ProductionOrder;
import com.industrialhub.backend.production.domain.ProductionOrderDisplayStatus;
import com.industrialhub.backend.production.domain.ProductionOrderStatus;
import com.industrialhub.backend.production.domain.ProductType;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Returns a paginated, filterable flat list of production orders for the tracking table view.
 * Extends {@link ListProductionOrdersUseCase} with support for displayStatus and overdue filters.
 * US-082 AC#6 / AC#8.
 */
@Service
public class ListProductionOrdersForTrackingUseCase {

    private final ProductionOrderRepository orderRepository;
    private final ImportProductionBatchRepository batchRepository;

    public ListProductionOrdersForTrackingUseCase(ProductionOrderRepository orderRepository,
                                                   ImportProductionBatchRepository batchRepository) {
        this.orderRepository = orderRepository;
        this.batchRepository = batchRepository;
    }

    /**
     * @param familyCode    optional filter by family code
     * @param displayStatus optional filter by computed display status (null = no filter)
     * @param overdue       optional filter; true = only overdue orders
     * @param productType   optional filter by product type
     * @param pageable      pagination / sorting
     */
    public ProductionOrderListResponse execute(String familyCode,
                                               String displayStatus,
                                               Boolean overdue,
                                               String productType,
                                               Pageable pageable) {

        // Resolve productType enum (null-safe)
        ProductType type = null;
        if (productType != null && !productType.isBlank()) {
            try {
                type = ProductType.valueOf(productType.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // unknown type treated as no filter
            }
        }

        boolean overdueOnly = Boolean.TRUE.equals(overdue);

        // Use existing filtered query — status param null means no raw-status filter here;
        // displayStatus filter (computed) will be applied in-memory on the fetched page.
        Page<ProductionOrder> page = orderRepository.findFiltered(
                familyCode, null, type, overdueOnly, LocalDate.now(), pageable);

        // Resolve target displayStatus filter
        ProductionOrderDisplayStatus targetDisplay = null;
        if (displayStatus != null && !displayStatus.isBlank()) {
            try {
                targetDisplay = ProductionOrderDisplayStatus.valueOf(displayStatus.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // unknown display status treated as no filter
            }
        }

        final ProductionOrderDisplayStatus finalTargetDisplay = targetDisplay;

        List<ProductionOrderTrackingItemResponse> content = page.getContent().stream()
                .map(o -> {
                    ProductionOrderDisplayStatus ds = toDisplayStatus(o.getStatus());
                    return ProductionOrderTrackingItemResponse.from(o, ds);
                })
                .filter(item -> finalTargetDisplay == null || finalTargetDisplay == item.displayStatus())
                .toList();

        LocalDateTime lastSyncAt = batchRepository
                .findFirstByTypeOrderByImportedAtDesc(ProductionImportType.PRODUCTION_ORDERS)
                .map(b -> b.getImportedAt())
                .orElse(null);

        return new ProductionOrderListResponse(
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                lastSyncAt
        );
    }

    /**
     * Maps raw {@link ProductionOrderStatus} to visual {@link ProductionOrderDisplayStatus}.
     * Mirrors the mapping in {@link GetProductionTrackingUseCase}.
     */
    private ProductionOrderDisplayStatus toDisplayStatus(ProductionOrderStatus status) {
        if (status == null) return null;
        return switch (status) {
            case PLANNED     -> ProductionOrderDisplayStatus.PLANNED;
            case RELEASED    -> ProductionOrderDisplayStatus.PLANNED;
            case IN_PROGRESS -> ProductionOrderDisplayStatus.IN_PROGRESS;
            case DONE        -> ProductionOrderDisplayStatus.DONE;
            case CANCELLED   -> ProductionOrderDisplayStatus.CANCELLED;
        };
    }
}
