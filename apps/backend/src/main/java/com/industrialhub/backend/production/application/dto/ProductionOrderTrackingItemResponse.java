package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.ProductionOrder;
import com.industrialhub.backend.production.domain.ProductionOrderDisplayStatus;
import com.industrialhub.backend.production.domain.ProductionOrderStatus;
import com.industrialhub.backend.production.domain.ProductType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single row in the tracking orders table view.
 * DisplayStatus is computed in Java from the raw status.
 * US-082 AC#6 / AC#8.
 */
public record ProductionOrderTrackingItemResponse(
        UUID id,
        String dynamicsOrderNumber,
        String productName,
        String familyCode,
        ProductType productType,
        ProductionOrderStatus status,
        ProductionOrderDisplayStatus displayStatus,
        BigDecimal plannedQty,
        BigDecimal producedQty,
        Double completionPct,
        LocalDate dueDate,
        boolean overdue
) {
    public static ProductionOrderTrackingItemResponse from(ProductionOrder o,
                                                            ProductionOrderDisplayStatus displayStatus) {
        Double completionPct = null;
        if (o.getPlannedQty() != null && o.getProducedQty() != null
                && o.getPlannedQty().compareTo(BigDecimal.ZERO) > 0) {
            completionPct = o.getProducedQty()
                    .divide(o.getPlannedQty(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }
        boolean overdue = o.getDueDate() != null
                && o.getDueDate().isBefore(LocalDate.now())
                && o.getStatus() != ProductionOrderStatus.DONE
                && o.getStatus() != ProductionOrderStatus.CANCELLED;
        return new ProductionOrderTrackingItemResponse(
                o.getId(),
                o.getDynamicsOrderNumber(),
                o.getProduct() != null ? o.getProduct().getName() : null,
                o.getFamily() != null ? o.getFamily().getCode() : null,
                o.getProduct() != null ? o.getProduct().getType() : null,
                o.getStatus(),
                displayStatus,
                o.getPlannedQty(),
                o.getProducedQty(),
                completionPct,
                o.getDueDate(),
                overdue
        );
    }
}
