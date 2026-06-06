package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.application.dto.CalibrationScheduleResponse;
import com.industrialhub.backend.maintenance.application.dto.CreateCalibrationScheduleRequest;
import com.industrialhub.backend.maintenance.domain.CalibrationSchedule;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentDecommissionedException;
import com.industrialhub.backend.maintenance.domain.EquipmentNotFoundException;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationScheduleRepository;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

@Service
public class CreateCalibrationScheduleUseCase {

    private final CalibrationScheduleRepository scheduleRepository;
    private final EquipmentRepository equipmentRepository;
    private final AuditService auditService;

    public CreateCalibrationScheduleUseCase(CalibrationScheduleRepository scheduleRepository,
                                             EquipmentRepository equipmentRepository,
                                             AuditService auditService) {
        this.scheduleRepository = scheduleRepository;
        this.equipmentRepository = equipmentRepository;
        this.auditService = auditService;
    }

    @Transactional
    public CalibrationScheduleResponse execute(CreateCalibrationScheduleRequest req, String principal) {
        Equipment equipment = equipmentRepository.findByIdAndActiveTrue(req.equipmentId())
            .orElseThrow(() -> new EquipmentNotFoundException(req.equipmentId()));

        if (equipment.getStatus() == EquipmentStatus.DECOMMISSIONED) {
            throw new EquipmentDecommissionedException(equipment.getId());
        }

        CalibrationSchedule schedule = CalibrationSchedule.builder()
            .equipment(equipment)
            .intervalDays(req.intervalDays())
            .nextDueAt(LocalDate.now().plusDays(req.intervalDays()))
            .externalProvider(req.externalProvider())
            .active(true)
            .createdBy(principal)
            .build();

        schedule = scheduleRepository.save(schedule);

        auditService.log(principal, AuditAction.CALIBRATION_SCHEDULE_CREATED,
            "CalibrationSchedule", schedule.getId(),
            Map.of("equipmentCode", equipment.getCode(), "intervalDays", req.intervalDays()));

        return CalibrationScheduleResponse.from(schedule);
    }
}
