package com.industrialhub.backend.common.application.dto;

public record EscalationRunResponse(
    int breachedNcs,
    int breachedWorkOrders
) {}
