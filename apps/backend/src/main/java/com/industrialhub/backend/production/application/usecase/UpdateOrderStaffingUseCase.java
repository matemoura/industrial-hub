package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.ProductionOrderStaffingResponse;
import com.industrialhub.backend.production.domain.ProductionOrder;
import com.industrialhub.backend.production.domain.ProductionOrderNotFoundException;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** ADR-030 Decisão 4 / US-086 AC#3 — edição manual de staffing por SUPERVISOR */
@Service
public class UpdateOrderStaffingUseCase {

    private final ProductionOrderRepository productionOrderRepository;

    public UpdateOrderStaffingUseCase(ProductionOrderRepository productionOrderRepository) {
        this.productionOrderRepository = productionOrderRepository;
    }

    @Transactional
    public ProductionOrderStaffingResponse execute(UUID orderId, int plannedPeople) {
        ProductionOrder order = productionOrderRepository.findById(orderId)
                .orElseThrow(() -> new ProductionOrderNotFoundException(orderId));
        order.setPlannedPeople(plannedPeople);
        order.setPeopleOverridden(true);
        return ProductionOrderStaffingResponse.from(order);
    }
}
