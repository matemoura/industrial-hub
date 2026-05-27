package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.ProductionOrderSummaryResponse;
import com.industrialhub.backend.production.domain.ProductionOrderStatus;
import com.industrialhub.backend.production.domain.ProductType;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ListProductionOrdersUseCase {

    private final ProductionOrderRepository orderRepository;

    public ListProductionOrdersUseCase(ProductionOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Page<ProductionOrderSummaryResponse> execute(
            String familyCode,
            ProductionOrderStatus status,
            ProductType productType,
            boolean overdueOnly,
            Pageable pageable) {
        return orderRepository.findFiltered(
                familyCode, status, productType, overdueOnly, LocalDate.now(), pageable)
                .map(ProductionOrderSummaryResponse::from);
    }
}
