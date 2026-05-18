package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.ScheduleResponse;
import com.industrialhub.backend.maintenance.infrastructure.MaintenanceScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GetScheduleListUseCase {

    private final MaintenanceScheduleRepository scheduleRepository;

    public GetScheduleListUseCase(MaintenanceScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> execute(UUID equipmentId) {
        if (equipmentId != null) {
            return scheduleRepository
                    .findByActiveTrueAndEquipmentIdOrderByNextRunAtAsc(equipmentId)
                    .stream()
                    .map(ScheduleResponse::from)
                    .toList();
        }
        return scheduleRepository
                .findByActiveTrueOrderByNextRunAtAsc()
                .stream()
                .map(ScheduleResponse::from)
                .toList();
    }
}
