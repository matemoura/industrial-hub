package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import com.industrialhub.backend.production.infrastructure.SterilizationLoadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RemoveOrderFromLoadUseCase {

    private final SterilizationLoadRepository loadRepository;
    private final ProductionOrderRepository orderRepository;

    public RemoveOrderFromLoadUseCase(SterilizationLoadRepository loadRepository,
                                       ProductionOrderRepository orderRepository) {
        this.loadRepository = loadRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public void execute(UUID loadId, UUID orderId) {
        SterilizationLoad load = loadRepository.findById(loadId)
                .orElseThrow(() -> new SterilizationLoadNotFoundException(loadId));

        if (load.getStatus() != LoadStatus.OPEN) {
            throw new InvalidLoadTransitionException(load.getStatus(), LoadStatus.OPEN);
        }

        ProductionOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ProductionOrderNotFoundException(orderId));

        order.setSterilizationLoad(null);
        orderRepository.save(order);
    }
}
