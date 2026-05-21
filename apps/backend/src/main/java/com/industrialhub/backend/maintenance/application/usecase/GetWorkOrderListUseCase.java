package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.WorkOrderResponse;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetWorkOrderListUseCase {

    private final WorkOrderRepository repository;

    public GetWorkOrderListUseCase(WorkOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderResponse> execute(UUID equipmentId, WorkOrderType type,
                                           WorkOrderStatus status, WorkOrderPriority priority,
                                           Pageable pageable) {
        return execute(equipmentId, type, status, priority, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderResponse> execute(UUID equipmentId, WorkOrderType type,
                                           WorkOrderStatus status, WorkOrderPriority priority,
                                           UUID shiftId, Pageable pageable) {
        return execute(equipmentId, type, status, priority, shiftId, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderResponse> execute(UUID equipmentId, WorkOrderType type,
                                           WorkOrderStatus status, WorkOrderPriority priority,
                                           UUID shiftId, Boolean slaBreached, Pageable pageable) {
        if (shiftId == null) {
            return repository.findWithFilters(equipmentId, type, status, priority, slaBreached, pageable)
                    .map(WorkOrderResponse::from);
        }
        return repository.findWithFiltersAndShift(equipmentId, type, status, priority, shiftId, slaBreached, pageable)
                .map(WorkOrderResponse::from);
    }
}
