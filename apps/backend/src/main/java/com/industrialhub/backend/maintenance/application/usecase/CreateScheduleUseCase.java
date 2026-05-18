package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.application.dto.CreateScheduleRequest;
import com.industrialhub.backend.maintenance.application.dto.ScheduleResponse;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentNotFoundException;
import com.industrialhub.backend.maintenance.domain.InactiveEquipmentScheduleException;
import com.industrialhub.backend.maintenance.domain.MaintenanceSchedule;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.maintenance.infrastructure.MaintenanceScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class CreateScheduleUseCase {

    private final EquipmentRepository equipmentRepository;
    private final MaintenanceScheduleRepository scheduleRepository;
    private final AuditService auditService;

    public CreateScheduleUseCase(EquipmentRepository equipmentRepository,
                                  MaintenanceScheduleRepository scheduleRepository,
                                  AuditService auditService) {
        this.equipmentRepository = equipmentRepository;
        this.scheduleRepository = scheduleRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ScheduleResponse execute(CreateScheduleRequest request, String username) {
        ScheduleRecurrenceHelper.validate(request.recurrence(), request.dayOfWeek(), request.dayOfMonth());

        Equipment equipment = equipmentRepository.findById(request.equipmentId())
                .orElseThrow(() -> new EquipmentNotFoundException(request.equipmentId()));

        if (!equipment.isActive()) {
            throw new InactiveEquipmentScheduleException();
        }

        LocalDate nextRunAt = ScheduleRecurrenceHelper.calculateNext(
                LocalDate.now(), request.recurrence(), request.dayOfWeek(), request.dayOfMonth());

        MaintenanceSchedule schedule = MaintenanceSchedule.builder()
                .equipment(equipment)
                .title(request.title())
                .description(request.description())
                .priority(request.priority())
                .recurrence(request.recurrence())
                .dayOfWeek(request.dayOfWeek())
                .dayOfMonth(request.dayOfMonth())
                .nextRunAt(nextRunAt)
                .active(true)
                .createdBy(username)
                .createdAt(LocalDateTime.now())
                .build();

        MaintenanceSchedule saved = scheduleRepository.save(schedule);

        auditService.log(username, AuditAction.SCHEDULE_CREATED, "MaintenanceSchedule", saved.getId(),
                Map.of("equipmentId", request.equipmentId().toString(), "recurrence", request.recurrence().name()));

        return ScheduleResponse.from(saved);
    }
}
