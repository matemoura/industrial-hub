package com.industrialhub.backend.qms.application.dto;

import com.industrialhub.backend.qms.domain.Supplier;

import java.time.LocalDate;
import java.util.UUID;

public record SupplierResponse(
    UUID id,
    String code,
    String name,
    String contactEmail,
    String contactPhone,
    String address,
    boolean active,
    LocalDate onboardedAt
) {
    public static SupplierResponse from(Supplier s) {
        return new SupplierResponse(
            s.getId(),
            s.getCode(),
            s.getName(),
            s.getContactEmail(),
            s.getContactPhone(),
            s.getAddress(),
            s.isActive(),
            s.getOnboardedAt()
        );
    }
}
