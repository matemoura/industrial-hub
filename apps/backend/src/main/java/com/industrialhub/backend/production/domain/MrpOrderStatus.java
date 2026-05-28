package com.industrialhub.backend.production.domain;

/** ADR-043 Decisão 3 — inclui SUPERSEDED para idempotência de runs */
public enum MrpOrderStatus {
    SUGGESTED,
    ACCEPTED,
    REJECTED,
    CONVERTED,
    SUPERSEDED
}
