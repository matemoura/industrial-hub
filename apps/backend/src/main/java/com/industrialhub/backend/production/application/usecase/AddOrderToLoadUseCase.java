package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import com.industrialhub.backend.production.infrastructure.SterilizationLoadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AddOrderToLoadUseCase {

    private final SterilizationLoadRepository loadRepository;
    private final ProductionOrderRepository orderRepository;
    private final AuditService auditService;

    public AddOrderToLoadUseCase(SterilizationLoadRepository loadRepository,
                                  ProductionOrderRepository orderRepository,
                                  AuditService auditService) {
        this.loadRepository = loadRepository;
        this.orderRepository = orderRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void execute(UUID loadId, UUID orderId, String username) {
        SterilizationLoad load = loadRepository.findById(loadId)
                .orElseThrow(() -> new SterilizationLoadNotFoundException(loadId));

        ProductionOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ProductionOrderNotFoundException(orderId));

        // Validate: OP must be DONE
        if (order.getStatus() != ProductionOrderStatus.DONE) {
            throw new IllegalArgumentException(
                    "OP %s não está com status DONE no Dynamics".formatted(order.getDynamicsOrderNumber()));
        }

        // Validate: product must require sterilization
        if (!order.getProduct().isRequiresSterilization()) {
            throw new IllegalArgumentException(
                    "Produto %s não requer esterilização".formatted(order.getProduct().getDynamicsCode()));
        }

        // Validate: OP not already allocated to an active load
        if (orderRepository.existsActiveAllocationForOrder(orderId)) {
            String existingLoadNumber = order.getSterilizationLoad() != null
                    ? order.getSterilizationLoad().getLoadNumber()
                    : "outra carga";
            throw new OrderAlreadyAllocatedException(order.getDynamicsOrderNumber(), existingLoadNumber);
        }

        order.setSterilizationLoad(load);
        orderRepository.save(order);

        auditService.log(username, AuditAction.STERILIZATION_ORDER_ALLOCATED,
                "SterilizationLoad", loadId.toString(),
                Map.of("orderId", orderId.toString(),
                       "loadNumber", load.getLoadNumber(),
                       "dynamicsOrderNumber", order.getDynamicsOrderNumber()));
    }
}
