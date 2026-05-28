package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.ProductionOrderStaffingResponse;
import com.industrialhub.backend.production.application.util.BusinessDaysCalculator;
import com.industrialhub.backend.production.domain.ProductionOrder;
import com.industrialhub.backend.production.domain.ProductionOrderNotFoundException;
import com.industrialhub.backend.production.domain.StaffingConfig;
import com.industrialhub.backend.production.infrastructure.CycleTimeRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/** ADR-030 Decisão 4 / US-086 AC#4 — reset para cálculo automático */
@Service
public class ResetOrderStaffingUseCase {

    private final ProductionOrderRepository productionOrderRepository;
    private final CycleTimeRepository cycleTimeRepository;
    private final GetStaffingConfigUseCase getStaffingConfig;

    public ResetOrderStaffingUseCase(
            ProductionOrderRepository productionOrderRepository,
            CycleTimeRepository cycleTimeRepository,
            GetStaffingConfigUseCase getStaffingConfig) {
        this.productionOrderRepository = productionOrderRepository;
        this.cycleTimeRepository = cycleTimeRepository;
        this.getStaffingConfig = getStaffingConfig;
    }

    @Transactional
    public ProductionOrderStaffingResponse execute(UUID orderId) {
        ProductionOrder order = productionOrderRepository.findById(orderId)
                .orElseThrow(() -> new ProductionOrderNotFoundException(orderId));

        order.setPeopleOverridden(false);
        order.setPlannedPeople(calculatePeople(order));

        return ProductionOrderStaffingResponse.from(order);
    }

    private Integer calculatePeople(ProductionOrder order) {
        if (order.getPlannedQty() == null) return null;
        var cycleTimeOpt = cycleTimeRepository.findTopByProductIdOrderByEffectiveDateDesc(
                order.getProduct().getId());
        if (cycleTimeOpt.isEmpty()) return null;

        double secondsPerUnit = cycleTimeOpt.get().getSecondsPerUnit();
        StaffingConfig config = getStaffingConfig.getOrCreate();
        int workdaySeconds = config.getShiftHours() * config.getShiftsPerDay() * 3600;

        LocalDate dueDate = order.getDueDate();
        int workdays = dueDate != null
                ? BusinessDaysCalculator.workdaysUntil(LocalDate.now(), dueDate)
                : 1;

        double totalSeconds = order.getPlannedQty().doubleValue() * secondsPerUnit;
        return (int) Math.ceil(totalSeconds / ((double) workdaySeconds * workdays));
    }
}
