package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.SterilizationLoadDetailResponse;
import com.industrialhub.backend.production.domain.SterilizationLoad;
import com.industrialhub.backend.production.domain.SterilizationLoadNotFoundException;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import com.industrialhub.backend.production.infrastructure.SterilizationLoadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class GetSterilizationLoadDetailUseCase {

    private final SterilizationLoadRepository loadRepository;
    private final ProductionOrderRepository orderRepository;

    public GetSterilizationLoadDetailUseCase(SterilizationLoadRepository loadRepository,
                                              ProductionOrderRepository orderRepository) {
        this.loadRepository = loadRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public SterilizationLoadDetailResponse execute(UUID loadId) {
        SterilizationLoad load = loadRepository.findById(loadId)
                .orElseThrow(() -> new SterilizationLoadNotFoundException(loadId));

        LocalDate today = LocalDate.now();
        List<SterilizationLoadDetailResponse.AllocatedOrderEntry> entries =
                orderRepository.findAllByLoadId(loadId).stream()
                        .map(po -> new SterilizationLoadDetailResponse.AllocatedOrderEntry(
                                po.getId(),
                                po.getDynamicsOrderNumber(),
                                po.getProduct().getDynamicsCode(),
                                po.getProduct().getName(),
                                po.getFamily() != null ? po.getFamily().getName() : null,
                                po.getPlannedQty(),
                                po.getDueDate(),
                                po.getDueDate() != null && po.getDueDate().isBefore(today)
                        ))
                        .toList();

        return SterilizationLoadDetailResponse.from(load, entries);
    }
}
