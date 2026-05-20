package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.WorkOrderResponse;
import com.industrialhub.backend.maintenance.domain.WorkOrderNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetWorkOrderDetailUseCase {
    private final WorkOrderRepository workOrderRepository;

    public WorkOrderResponse execute(UUID id) {
        return workOrderRepository.findById(id)
            .map(WorkOrderResponse::from)
            .orElseThrow(() -> new WorkOrderNotFoundException(id));
    }
}
