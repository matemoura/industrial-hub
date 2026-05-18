package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.application.dto.ScheduleResponse;
import com.industrialhub.backend.maintenance.application.dto.UpdateScheduleRequest;
import com.industrialhub.backend.maintenance.domain.ScheduleNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.MaintenanceScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class UpdateScheduleUseCase {

    private final MaintenanceScheduleRepository scheduleRepository;
    private final AuditService auditService;

    public UpdateScheduleUseCase(MaintenanceScheduleRepository scheduleRepository,
                                  AuditService auditService) {
        this.scheduleRepository = scheduleRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ScheduleResponse execute(UUID id, UpdateScheduleRequest request, String username) {
        ScheduleRecurrenceHelper.validate(request.recurrence(), request.dayOfWeek(), request.dayOfMonth());

        var schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ScheduleNotFoundException(id));

        schedule.setTitle(request.title());
        schedule.setDescription(request.description());
        schedule.setPriority(request.priority());
        schedule.setRecurrence(request.recurrence());
        schedule.setDayOfWeek(request.dayOfWeek());
        schedule.setDayOfMonth(request.dayOfMonth());
        schedule.setNextRunAt(ScheduleRecurrenceHelper.calculateNext(
                LocalDate.now(), request.recurrence(), request.dayOfWeek(), request.dayOfMonth()));

        var saved = scheduleRepository.save(schedule);

        auditService.log(username, AuditAction.SCHEDULE_UPDATED, "MaintenanceSchedule", id,
                Map.of("recurrence", request.recurrence().name(), "title", request.title()));

        return ScheduleResponse.from(saved);
    }
}
