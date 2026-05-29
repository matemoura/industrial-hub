package com.industrialhub.backend.production.application.dto;

/**
 * ADR-044 Decisão 5 — linha do relatório planned vs actual por produto/família.
 * efficiency é null quando plannedQty = 0 (sem divisão por zero).
 */
public record PlanningSummaryRow(
        String familyCode,
        String familyName,
        String productCode,
        String productName,
        Integer plannedQty,
        Integer producedQty,
        Double efficiency,
        Integer pendingMrpQty
) {}
