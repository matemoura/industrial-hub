package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.WorkOrderMetricsResponse;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class GetWorkOrderMetricsUseCase {

    private final WorkOrderRepository repository;

    public GetWorkOrderMetricsUseCase(WorkOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public WorkOrderMetricsResponse execute(UUID equipmentId) {
        List<WorkOrder> completed = repository.findCompletedCorrectiveForMetrics(equipmentId);

        Double mttr = null;
        if (!completed.isEmpty()) {
            double avgDurationSeconds = completed.stream()
                    .mapToLong(wo -> ChronoUnit.SECONDS.between(wo.getStartedAt(), wo.getClosedAt()))
                    .average()
                    .orElse(0.0);
            mttr = avgDurationSeconds / 3600.0;
        }

        long totalOrders = repository.countByEquipmentId(equipmentId);
        long openOrders = repository.countOpenByEquipmentId(equipmentId);

        return new WorkOrderMetricsResponse(mttr, totalOrders, openOrders);
    }
}
