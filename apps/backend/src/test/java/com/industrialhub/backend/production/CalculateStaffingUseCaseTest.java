package com.industrialhub.backend.production;

import com.industrialhub.backend.production.application.dto.ProductionOrderStaffingResponse;
import com.industrialhub.backend.production.application.usecase.GetStaffingConfigUseCase;
import com.industrialhub.backend.production.application.usecase.ResetOrderStaffingUseCase;
import com.industrialhub.backend.production.application.usecase.UpdateOrderStaffingUseCase;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.CycleTimeRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import com.industrialhub.backend.production.infrastructure.StaffingConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalculateStaffingUseCaseTest {

    @Mock ProductionOrderRepository productionOrderRepository;
    @Mock CycleTimeRepository cycleTimeRepository;
    @Mock StaffingConfigRepository staffingConfigRepository;

    private StaffingConfig defaultConfig() {
        StaffingConfig config = new StaffingConfig();
        config.setShiftHours(8);
        config.setShiftsPerDay(1);
        config.setUpdatedAt(java.time.LocalDateTime.now());
        config.setUpdatedBy("system");
        return config;
    }

    private ProductionOrder makeOrder(int plannedQty, LocalDate dueDate) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setDynamicsCode("P001");

        ProductionOrder order = new ProductionOrder();
        order.setId(UUID.randomUUID());
        order.setDynamicsOrderNumber("OP-001");
        order.setProduct(product);
        order.setPlannedQty(BigDecimal.valueOf(plannedQty));
        order.setDueDate(dueDate);
        order.setStatus(ProductionOrderStatus.PLANNED);
        order.setPeopleOverridden(false);
        return order;
    }

    @Test
    void shouldUpdateStaffingManuallyAndSetOverride() {
        ProductionOrder order = makeOrder(100, LocalDate.now().plusDays(10));
        when(productionOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        UpdateOrderStaffingUseCase updateUseCase = new UpdateOrderStaffingUseCase(productionOrderRepository);
        ProductionOrderStaffingResponse result = updateUseCase.execute(order.getId(), 5);

        assertThat(result.plannedPeople()).isEqualTo(5);
        assertThat(result.peopleOverridden()).isTrue();
    }

    @Test
    void shouldPreservePeopleOverriddenOnUpdate() {
        ProductionOrder order = makeOrder(100, LocalDate.now().plusDays(10));
        order.setPlannedPeople(3);
        order.setPeopleOverridden(true);
        when(productionOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        UpdateOrderStaffingUseCase updateUseCase = new UpdateOrderStaffingUseCase(productionOrderRepository);
        updateUseCase.execute(order.getId(), 7);

        assertThat(order.getPlannedPeople()).isEqualTo(7);
        assertThat(order.isPeopleOverridden()).isTrue();
    }

    @Test
    void shouldResetStaffingAndRecalculate() {
        // 100 units * 288 sec/unit = 28800 total seconds
        // 1 shift * 8h * 3600 = 28800 sec/day
        // dueDate = today + 5 workdays → 1 person needed
        ProductionOrder order = makeOrder(100, LocalDate.now().plusDays(7));
        order.setPlannedPeople(10);
        order.setPeopleOverridden(true);
        StaffingConfig config = defaultConfig();
        when(productionOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(staffingConfigRepository.findFirstList(any())).thenReturn(List.of(config));
        when(staffingConfigRepository.save(any())).thenReturn(config);

        CycleTime cycleTime = new CycleTime();
        cycleTime.setSecondsPerUnit(288.0); // 100 * 288 / (28800 * workdays)
        when(cycleTimeRepository.findTopByProductIdOrderByEffectiveDateDesc(any()))
                .thenReturn(Optional.of(cycleTime));

        GetStaffingConfigUseCase getConfig = new GetStaffingConfigUseCase(staffingConfigRepository);
        ResetOrderStaffingUseCase resetUseCase = new ResetOrderStaffingUseCase(
                productionOrderRepository, cycleTimeRepository, getConfig);

        ProductionOrderStaffingResponse result = resetUseCase.execute(order.getId());

        assertThat(result.peopleOverridden()).isFalse();
        assertThat(result.plannedPeople()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldReturnNullPeopleWhenNoCycleTime() {
        ProductionOrder order = makeOrder(100, LocalDate.now().plusDays(7));
        when(productionOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        // staffingConfig NOT stubbed — code returns null before calling getOrCreate() when cycleTime is absent
        when(cycleTimeRepository.findTopByProductIdOrderByEffectiveDateDesc(any()))
                .thenReturn(Optional.empty());

        GetStaffingConfigUseCase getConfig = new GetStaffingConfigUseCase(staffingConfigRepository);
        ResetOrderStaffingUseCase resetUseCase = new ResetOrderStaffingUseCase(
                productionOrderRepository, cycleTimeRepository, getConfig);

        ProductionOrderStaffingResponse result = resetUseCase.execute(order.getId());

        assertThat(result.plannedPeople()).isNull();
        assertThat(result.peopleOverridden()).isFalse();
    }
}
