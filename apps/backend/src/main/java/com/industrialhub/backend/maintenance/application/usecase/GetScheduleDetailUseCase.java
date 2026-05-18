package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.ScheduleResponse;
import com.industrialhub.backend.maintenance.domain.ScheduleNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.MaintenanceScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetScheduleDetailUseCase {

    private final MaintenanceScheduleRepository scheduleRepository;

    public GetScheduleDetailUseCase(MaintenanceScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    @Transactional(readOnly = true)
    public ScheduleResponse execute(UUID id) {
        return scheduleRepository.findById(id)
                .map(ScheduleResponse::from)
                .orElseThrow(() -> new ScheduleNotFoundException(id));
    }
}
