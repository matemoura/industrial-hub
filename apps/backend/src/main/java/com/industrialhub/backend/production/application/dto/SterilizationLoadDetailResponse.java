package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.LoadStatus;
import com.industrialhub.backend.production.domain.SterilizationLoad;
import com.industrialhub.backend.production.domain.SterilizationMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SterilizationLoadDetailResponse(
        UUID id,
        String loadNumber,
        LoadStatus status,
        SterilizationMethod method,
        String sterilizerName,
        LocalDate sterilizationDate,
        String batchCode,
        String notes,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime closedAt,
        LocalDateTime releasedAt,
        List<AllocatedOrderEntry> orders,
        int totalOrders,
        BigDecimal totalPlannedQty
) {
    public record AllocatedOrderEntry(
            UUID id,
            String dynamicsOrderNumber,
            String productCode,
            String productName,
            String familyName,
            BigDecimal plannedQty,
            LocalDate dueDate,
            boolean overdue
    ) {}

    public static SterilizationLoadDetailResponse from(SterilizationLoad sl, List<AllocatedOrderEntry> orders) {
        BigDecimal totalQty = orders.stream()
                .map(e -> e.plannedQty() != null ? e.plannedQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SterilizationLoadDetailResponse(
                sl.getId(),
                sl.getLoadNumber(),
                sl.getStatus(),
                sl.getMethod(),
                sl.getSterilizer() != null ? sl.getSterilizer().getName() : null,
                sl.getSterilizationDate(),
                sl.getBatchCode(),
                sl.getNotes(),
                sl.getCreatedBy(),
                sl.getCreatedAt(),
                sl.getClosedAt(),
                sl.getReleasedAt(),
                orders,
                orders.size(),
                totalQty
        );
    }
}
