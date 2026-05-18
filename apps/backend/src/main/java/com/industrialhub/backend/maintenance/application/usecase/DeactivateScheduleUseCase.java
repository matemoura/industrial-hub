package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.domain.ScheduleNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.MaintenanceScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class DeactivateScheduleUseCase {

    private final MaintenanceScheduleRepository scheduleRepository;
    private final AuditService auditService;

    public DeactivateScheduleUseCase(MaintenanceScheduleRepository scheduleRepository,
                                      AuditService auditService) {
        this.scheduleRepository = scheduleRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void execute(UUID id, String username) {
        var schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ScheduleNotFoundException(id));

        schedule.setActive(false);
        scheduleRepository.save(schedule);

        auditService.log(username, AuditAction.SCHEDULE_DEACTIVATED, "MaintenanceSchedule", id,
                Map.of("equipmentId", schedule.getEquipment().getId().toString()));
    }
}
