package com.industrialhub.backend.production.application.dto;

/** Necessidade de compra de RAW_MATERIAL gerada pelo motor MRP */
public record PurchaseNeedResponse(
        String productCode,
        String productName,
        Integer quantity,
        String unit
) {}
