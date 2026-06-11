package com.industrialhub.backend.production.application.dto;

public record ImportPermissionsResponse(
        boolean canImportProducts,
        boolean canImportBom,
        boolean canImportCycleTimes,
        boolean canImportStock,
        boolean canImportOrders,
        boolean canImportOeeData
) {}
