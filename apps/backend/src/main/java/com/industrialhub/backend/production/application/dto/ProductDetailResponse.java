package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.Product;
import com.industrialhub.backend.production.domain.ProductType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProductDetailResponse(
        UUID id,
        String dynamicsCode,
        String name,
        ProductType type,
        String familyCode,
        String familyName,
        String unit,
        Integer leadTimeDays,
        Integer minStockQty,
        Integer batchSize,
        boolean requiresSterilization,
        boolean active,
        LocalDateTime lastSyncAt,
        Integer currentStockQty,
        Double currentCycleTimeSeconds
) {
    public static ProductDetailResponse from(Product p, Integer currentStockQty, Double currentCycleTimeSeconds) {
        return new ProductDetailResponse(
                p.getId(),
                p.getDynamicsCode(),
                p.getName(),
                p.getType(),
                p.getFamily() != null ? p.getFamily().getCode() : null,
                p.getFamily() != null ? p.getFamily().getName() : null,
                p.getUnit(),
                p.getLeadTimeDays(),
                p.getMinStockQty(),
                p.getBatchSize(),
                p.isRequiresSterilization(),
                p.isActive(),
                p.getLastSyncAt(),
                currentStockQty,
                currentCycleTimeSeconds
        );
    }
}
