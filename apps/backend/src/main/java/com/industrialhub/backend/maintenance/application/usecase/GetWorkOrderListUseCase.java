package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.PlantContext;
import com.industrialhub.backend.maintenance.application.dto.WorkOrderResponse;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GetWorkOrderListUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetWorkOrderListUseCase.class);

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
        boolean isAdmin = PlantContext.isAdminContext();
        List<UUID> plantIds = PlantContext.current();

        if (!isAdmin && plantIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        if (shiftId != null && !isAdmin) {
            log.debug("shiftId filter ignored for non-ADMIN user — plant-scoped query in use");
        }

        if (isAdmin) {
            // ADMIN: no plant filter
            if (shiftId == null) {
                return repository.findWithFilters(equipmentId, type, status, priority, slaBreached, pageable)
                        .map(WorkOrderResponse::from);
            }
            return repository.findWithFiltersAndShift(equipmentId, type, status, priority, shiftId, slaBreached, pageable)
                    .map(WorkOrderResponse::from);
        } else {
            // Non-ADMIN: filter by plant (shift filter ignored when plant filtering)
            return repository.findWithFiltersAndPlantIds(equipmentId, type, status, priority, slaBreached, plantIds, pageable)
                    .map(WorkOrderResponse::from);
        }
    }
}
