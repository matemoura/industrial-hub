package com.industrialhub.backend.production;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.production.application.usecase.AddOrderToLoadUseCase;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import com.industrialhub.backend.production.infrastructure.SterilizationLoadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddOrderToLoadUseCaseTest {

    @Mock SterilizationLoadRepository loadRepository;
    @Mock ProductionOrderRepository orderRepository;
    @Mock AuditService auditService;
    @InjectMocks AddOrderToLoadUseCase useCase;

    private SterilizationLoad openLoad() {
        SterilizationLoad load = new SterilizationLoad();
        load.setId(UUID.randomUUID());
        load.setLoadNumber("CARGA-2026-001");
        load.setStatus(LoadStatus.OPEN);
        return load;
    }

    private ProductionOrder doneOrderRequiringSterilization() {
        Product product = new Product();
        product.setDynamicsCode("P001");
        product.setName("Produto A");
        product.setRequiresSterilization(true);

        ProductionOrder order = new ProductionOrder();
        order.setId(UUID.randomUUID());
        order.setDynamicsOrderNumber("OP-2026-0001");
        order.setStatus(ProductionOrderStatus.DONE);
        order.setProduct(product);
        return order;
    }

    @Test
    void shouldAllocateValidOrderToLoad() {
        SterilizationLoad load = openLoad();
        ProductionOrder order = doneOrderRequiringSterilization();

        when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.existsActiveAllocationForOrder(order.getId())).thenReturn(false);

        useCase.execute(load.getId(), order.getId(), "supervisor1");

        verify(orderRepository).save(argThat(o -> o.getSterilizationLoad() == load));
        verify(auditService).log(eq("supervisor1"), any(), anyString(), anyString(), anyMap());
    }

    @Test
    void shouldThrow409WhenOrderAlreadyAllocated() {
        SterilizationLoad load = openLoad();
        ProductionOrder order = doneOrderRequiringSterilization();
        order.setSterilizationLoad(load);

        when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.existsActiveAllocationForOrder(order.getId())).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(load.getId(), order.getId(), "supervisor1"))
                .isInstanceOf(OrderAlreadyAllocatedException.class)
                .hasMessageContaining("OP-2026-0001");
    }

    @Test
    void shouldThrowWhenOrderNotDone() {
        SterilizationLoad load = openLoad();
        ProductionOrder order = doneOrderRequiringSterilization();
        order.setStatus(ProductionOrderStatus.IN_PROGRESS);

        when(loadRepository.findById(load.getId())).thenReturn(Optional.of(load));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.execute(load.getId(), order.getId(), "supervisor1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DONE");
    }

    @Test
    void shouldAllowReAllocationAfterRejectedLoad() {
        // OP previously in a REJECTED load — sterilizationLoad is null after clearLoadForAllOrdersInLoad
        SterilizationLoad newLoad = openLoad();
        ProductionOrder order = doneOrderRequiringSterilization();
        order.setSterilizationLoad(null); // cleared by REJECTED transition

        when(loadRepository.findById(newLoad.getId())).thenReturn(Optional.of(newLoad));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.existsActiveAllocationForOrder(order.getId())).thenReturn(false);

        useCase.execute(newLoad.getId(), order.getId(), "supervisor1");

        verify(orderRepository).save(argThat(o -> o.getSterilizationLoad() == newLoad));
    }
}
