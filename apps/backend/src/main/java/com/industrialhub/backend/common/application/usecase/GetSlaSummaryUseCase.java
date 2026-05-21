package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.SlaSummaryResponse;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetSlaSummaryUseCase {

    private final NonConformanceRepository ncRepository;
    private final WorkOrderRepository workOrderRepository;

    @Transactional(readOnly = true)
    public SlaSummaryResponse execute() {
        long totalBreachedNcs = ncRepository.countBreached();
        long totalBreachedWorkOrders = workOrderRepository.countBreached();
        long totalOpenNcs = ncRepository.countOpen();
        long totalOpenWorkOrders = workOrderRepository.countOpen();

        return new SlaSummaryResponse(
            totalBreachedNcs,
            totalBreachedWorkOrders,
            totalOpenNcs,
            totalOpenWorkOrders
        );
    }
}
