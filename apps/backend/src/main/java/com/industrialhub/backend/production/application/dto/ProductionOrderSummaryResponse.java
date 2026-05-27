package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.ProductionOrder;
import com.industrialhub.backend.production.domain.ProductionOrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

public record ProductionOrderSummaryResponse(
        UUID id,
        String dynamicsOrderNumber,
        String productName,
        String familyCode,
        ProductionOrderStatus status,
        BigDecimal plannedQty,
        BigDecimal producedQty,
        Double completionPct,
        LocalDate dueDate,
        boolean overdue
) {
    public static ProductionOrderSummaryResponse from(ProductionOrder o) {
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
        return new ProductionOrderSummaryResponse(
                o.getId(),
                o.getDynamicsOrderNumber(),
                o.getProduct() != null ? o.getProduct().getName() : null,
                o.getFamily() != null ? o.getFamily().getCode() : null,
                o.getStatus(),
                o.getPlannedQty(),
                o.getProducedQty(),
                completionPct,
                o.getDueDate(),
                overdue
        );
    }
}
