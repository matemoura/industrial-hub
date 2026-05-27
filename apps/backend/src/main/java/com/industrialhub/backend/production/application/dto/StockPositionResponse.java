package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.StockSnapshot;

import java.time.LocalDate;
import java.util.UUID;

public record StockPositionResponse(
        UUID productId,
        String productCode,
        String productName,
        String familyCode,
        int currentQty,
        Integer minStockQty,
        boolean belowMin,
        LocalDate lastSnapshotDate
) {
    public static StockPositionResponse from(StockSnapshot s) {
        int qty = s.getQty() != null ? s.getQty() : 0;
        Integer min = s.getProduct().getMinStockQty();
        boolean below = min != null && qty < min;
        return new StockPositionResponse(
                s.getProduct().getId(),
                s.getProduct().getDynamicsCode(),
                s.getProduct().getName(),
                s.getProduct().getFamily() != null ? s.getProduct().getFamily().getCode() : null,
                qty,
                min,
                below,
                s.getSnapshotDate()
        );
    }
}
