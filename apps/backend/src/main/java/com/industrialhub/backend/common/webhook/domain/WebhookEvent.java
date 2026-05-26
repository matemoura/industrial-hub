package com.industrialhub.backend.common.webhook.domain;

public enum WebhookEvent {
    NC_CREATED,
    NC_STATUS_CHANGED,
    NC_CRITICAL_OPENED,
    WORK_ORDER_CREATED,
    WORK_ORDER_STATUS_CHANGED,
    EQUIPMENT_DECOMMISSIONED,
    SLA_BREACHED,
    TEST
}
