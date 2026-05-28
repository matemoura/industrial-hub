package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.MrpPlannedOrderResponse;
import com.industrialhub.backend.production.domain.MrpOrderStatus;
import com.industrialhub.backend.production.infrastructure.MrpPlannedOrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetMrpSuggestionsUseCase {

    private final MrpPlannedOrderRepository mrpPlannedOrderRepository;

    public GetMrpSuggestionsUseCase(MrpPlannedOrderRepository mrpPlannedOrderRepository) {
        this.mrpPlannedOrderRepository = mrpPlannedOrderRepository;
    }

    public List<MrpPlannedOrderResponse> execute() {
        return mrpPlannedOrderRepository.findByStatus(MrpOrderStatus.SUGGESTED).stream()
                .map(MrpPlannedOrderResponse::from)
                .toList();
    }
}
