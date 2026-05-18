package com.industrialhub.backend.qms.application.dto;

import java.util.UUID;

public record SupplierQualityScore(
    UUID supplierId,
    String supplierName,
    long totalNcs,
    long criticalNcs,
    long highNcs,
    double qualityScore
) {}
