package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.MrpOrderStatus;
import com.industrialhub.backend.production.domain.MrpPlannedOrder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record MrpPlannedOrderResponse(
        UUID id,
        String productCode,
        String productName,
        String familyName,
        Integer suggestedQty,
        Integer adjustedQty,
        LocalDate suggestedStartDate,
        LocalDate suggestedDueDate,
        MrpOrderStatus status,
        String rejectionReason,
        String reviewedBy,
        LocalDateTime reviewedAt
) {
    public static MrpPlannedOrderResponse from(MrpPlannedOrder o) {
        return new MrpPlannedOrderResponse(
                o.getId(),
                o.getProduct().getDynamicsCode(),
                o.getProduct().getName(),
                o.getFamily() != null ? o.getFamily().getName() : null,
                o.getSuggestedQty(),
                o.getAdjustedQty(),
                o.getSuggestedStartDate(),
                o.getSuggestedDueDate(),
                o.getStatus(),
                o.getRejectionReason(),
                o.getReviewedBy(),
                o.getReviewedAt()
        );
    }
}
