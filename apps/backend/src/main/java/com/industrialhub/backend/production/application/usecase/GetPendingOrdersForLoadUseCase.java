package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.PendingOrderForLoadResponse;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetPendingOrdersForLoadUseCase {

    private final ProductionOrderRepository orderRepository;

    public GetPendingOrdersForLoadUseCase(ProductionOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public List<PendingOrderForLoadResponse> execute() {
        return orderRepository.findPendingForSterilization().stream()
                .map(PendingOrderForLoadResponse::from)
                .toList();
    }
}
