package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.ScheduleResponse;
import com.industrialhub.backend.maintenance.application.dto.UpdateScheduleRequest;
import com.industrialhub.backend.maintenance.domain.ScheduleNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.MaintenanceScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class UpdateScheduleUseCase {

    private final MaintenanceScheduleRepository scheduleRepository;

    public UpdateScheduleUseCase(MaintenanceScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    @Transactional
    public ScheduleResponse execute(UUID id, UpdateScheduleRequest request) {
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

        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }
}
