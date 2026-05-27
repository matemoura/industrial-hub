package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.ProductFamily;

import java.util.UUID;

public record ProductFamilyResponse(UUID id, String code, String name, boolean active) {
    public static ProductFamilyResponse from(ProductFamily f) {
        return new ProductFamilyResponse(f.getId(), f.getCode(), f.getName(), f.isActive());
    }
}
