package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.Product;
import com.industrialhub.backend.production.domain.ProductType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProductSummaryResponse(
        UUID id,
        String dynamicsCode,
        String name,
        ProductType type,
        String familyCode,
        String familyName,
        String unit,
        boolean requiresSterilization,
        boolean active,
        LocalDateTime lastSyncAt
) {
    public static ProductSummaryResponse from(Product p) {
        return new ProductSummaryResponse(
                p.getId(),
                p.getDynamicsCode(),
                p.getName(),
                p.getType(),
                p.getFamily() != null ? p.getFamily().getCode() : null,
                p.getFamily() != null ? p.getFamily().getName() : null,
                p.getUnit(),
                p.isRequiresSterilization(),
                p.isActive(),
                p.getLastSyncAt()
        );
    }
}
