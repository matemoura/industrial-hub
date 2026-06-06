package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.domain.CalibrationSchedule;
import com.industrialhub.backend.maintenance.domain.CalibrationScheduleNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class DeactivateCalibrationScheduleUseCase {

    private final CalibrationScheduleRepository scheduleRepository;
    private final AuditService auditService;

    public DeactivateCalibrationScheduleUseCase(CalibrationScheduleRepository scheduleRepository,
                                                 AuditService auditService) {
        this.scheduleRepository = scheduleRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void execute(UUID id, String principal) {
        CalibrationSchedule schedule = scheduleRepository.findById(id)
            .orElseThrow(() -> new CalibrationScheduleNotFoundException(id));

        schedule.setActive(false);
        scheduleRepository.save(schedule);

        auditService.log(principal, AuditAction.CALIBRATION_SCHEDULE_DEACTIVATED,
            "CalibrationSchedule", id, Map.of("equipmentCode", schedule.getEquipment().getCode()));
    }
}
