package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.ProductComponent;
import com.industrialhub.backend.production.domain.ProductType;

/**
 * Linha de BOM para o endpoint GET /products/{code}/bom (ADR-044).
 */
public record BomComponentRow(
        String componentCode,
        String componentName,
        Double quantity,
        String unit,
        Integer level,
        ProductType productType
) {
    public static BomComponentRow from(ProductComponent pc) {
        return new BomComponentRow(
                pc.getComponentProduct().getDynamicsCode(),
                pc.getComponentProduct().getName(),
                pc.getQuantity(),
                pc.getUnit(),
                pc.getLevel(),
                pc.getComponentProduct().getType()
        );
    }
}
