package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.ProductionOrder;

import java.util.UUID;

public record ProductionOrderStaffingResponse(
        UUID id,
        String dynamicsOrderNumber,
        Integer plannedPeople,
        boolean peopleOverridden
) {
    public static ProductionOrderStaffingResponse from(ProductionOrder o) {
        return new ProductionOrderStaffingResponse(
                o.getId(),
                o.getDynamicsOrderNumber(),
                o.getPlannedPeople(),
                o.isPeopleOverridden()
        );
    }
}
