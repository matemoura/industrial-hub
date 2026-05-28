package com.industrialhub.backend.production.domain;

/**
 * Visual display status for production orders in the kanban tracking view.
 * Calculated in Java from ProductionOrderStatus — never persisted.
 * ADR-041 Decisão 2.
 */
public enum ProductionOrderDisplayStatus {
    PLANNED,
    IN_PROGRESS,
    PENDING_QUALITY,
    PENDING_STERILIZATION,
    IN_LOAD,
    FINISHED,
    DONE,
    CANCELLED
}
