package com.industrialhub.backend.maintenance.application.dto;

import com.industrialhub.backend.maintenance.domain.SparePart;

import java.util.UUID;

public record SparePartResponse(
    UUID id,
    String code,
    String name,
    String category,
    String unit,
    Integer stockQty,
    Integer minStockQty,
    boolean active,
    boolean belowMin
) {
    public static SparePartResponse from(SparePart p) {
        return new SparePartResponse(
            p.getId(), p.getCode(), p.getName(), p.getCategory(), p.getUnit(),
            p.getStockQty(), p.getMinStockQty(), p.isActive(),
            p.getStockQty() != null && p.getMinStockQty() != null && p.getStockQty() < p.getMinStockQty()
        );
    }
}
