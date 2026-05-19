package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.PlannedDowntimeResponse;
import com.industrialhub.backend.oee.infrastructure.PlannedDowntimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class GetPlannedDowntimesUseCase {

    private final PlannedDowntimeRepository plannedDowntimeRepository;

    public GetPlannedDowntimesUseCase(PlannedDowntimeRepository plannedDowntimeRepository) {
        this.plannedDowntimeRepository = plannedDowntimeRepository;
    }

    @Transactional(readOnly = true)
    public List<PlannedDowntimeResponse> execute(LocalDate date, UUID equipmentId) {
        LocalDateTime dayStart = date != null ? date.atStartOfDay() : null;
        LocalDateTime dayEnd   = date != null ? date.plusDays(1).atStartOfDay() : null;
        return plannedDowntimeRepository
                .findByOptionalFilters(dayStart, dayEnd, equipmentId)
                .stream()
                .map(PlannedDowntimeResponse::from)
                .toList();
    }
}
