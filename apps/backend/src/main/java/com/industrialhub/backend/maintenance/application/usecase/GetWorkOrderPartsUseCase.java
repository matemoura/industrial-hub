package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.WorkOrderPartResponse;
import com.industrialhub.backend.maintenance.domain.WorkOrderNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderPartRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class GetWorkOrderPartsUseCase {

    private final WorkOrderPartRepository workOrderPartRepository;
    private final WorkOrderRepository workOrderRepository;

    public GetWorkOrderPartsUseCase(WorkOrderPartRepository workOrderPartRepository,
                                     WorkOrderRepository workOrderRepository) {
        this.workOrderPartRepository = workOrderPartRepository;
        this.workOrderRepository = workOrderRepository;
    }

    public List<WorkOrderPartResponse> execute(UUID workOrderId) {
        if (!workOrderRepository.existsById(workOrderId)) {
            throw new WorkOrderNotFoundException(workOrderId);
        }
        return workOrderPartRepository.findByWorkOrderIdWithPart(workOrderId)
                .stream()
                .map(WorkOrderPartResponse::from)
                .toList();
    }
}
