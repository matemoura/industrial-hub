package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.CalibrationScheduleResponse;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class GetCalibrationSchedulesUseCase {

    private final CalibrationScheduleRepository scheduleRepository;

    public GetCalibrationSchedulesUseCase(CalibrationScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    @Transactional(readOnly = true)
    public List<CalibrationScheduleResponse> execute(UUID equipmentId, Boolean overdue) {
        List<?> schedules;

        if (equipmentId != null) {
            schedules = scheduleRepository.findByEquipmentIdAndActiveTrue(equipmentId);
        } else if (Boolean.TRUE.equals(overdue)) {
            schedules = scheduleRepository.findByActiveTrueAndNextDueAtBefore(LocalDate.now());
        } else {
            schedules = scheduleRepository.findByActiveTrue();
        }

        return schedules.stream()
            .map(s -> CalibrationScheduleResponse.from(
                (com.industrialhub.backend.maintenance.domain.CalibrationSchedule) s))
            .toList();
    }
}
