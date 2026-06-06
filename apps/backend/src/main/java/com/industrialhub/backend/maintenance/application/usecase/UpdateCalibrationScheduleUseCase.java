package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.application.dto.CalibrationScheduleResponse;
import com.industrialhub.backend.maintenance.application.dto.UpdateCalibrationScheduleRequest;
import com.industrialhub.backend.maintenance.domain.CalibrationSchedule;
import com.industrialhub.backend.maintenance.domain.CalibrationScheduleNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class UpdateCalibrationScheduleUseCase {

    private final CalibrationScheduleRepository scheduleRepository;
    private final AuditService auditService;

    public UpdateCalibrationScheduleUseCase(CalibrationScheduleRepository scheduleRepository,
                                             AuditService auditService) {
        this.scheduleRepository = scheduleRepository;
        this.auditService = auditService;
    }

    @Transactional
    public CalibrationScheduleResponse execute(UUID id, UpdateCalibrationScheduleRequest req, String principal) {
        CalibrationSchedule schedule = scheduleRepository.findById(id)
            .orElseThrow(() -> new CalibrationScheduleNotFoundException(id));

        schedule.setIntervalDays(req.intervalDays());
        schedule.setExternalProvider(req.externalProvider());

        // Recalculate nextDueAt
        LocalDate base = schedule.getLastCalibratedAt() != null
            ? schedule.getLastCalibratedAt()
            : LocalDate.now();
        schedule.setNextDueAt(base.plusDays(req.intervalDays()));

        schedule = scheduleRepository.save(schedule);

        auditService.log(principal, AuditAction.CALIBRATION_SCHEDULE_UPDATED,
            "CalibrationSchedule", schedule.getId(),
            Map.of("intervalDays", req.intervalDays()));

        return CalibrationScheduleResponse.from(schedule);
    }
}
