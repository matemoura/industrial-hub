package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.ProductionOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PendingOrderForLoadResponse(
        UUID id,
        String dynamicsOrderNumber,
        String productCode,
        String productName,
        String familyName,
        BigDecimal plannedQty,
        LocalDate dueDate,
        boolean overdue
) {
    public static PendingOrderForLoadResponse from(ProductionOrder po) {
        return new PendingOrderForLoadResponse(
                po.getId(),
                po.getDynamicsOrderNumber(),
                po.getProduct().getDynamicsCode(),
                po.getProduct().getName(),
                po.getFamily() != null ? po.getFamily().getName() : null,
                po.getPlannedQty(),
                po.getDueDate(),
                po.getDueDate() != null && po.getDueDate().isBefore(LocalDate.now())
        );
    }
}
