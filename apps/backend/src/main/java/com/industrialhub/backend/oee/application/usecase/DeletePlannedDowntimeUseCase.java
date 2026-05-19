package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.domain.PlannedDowntimeNotFoundException;
import com.industrialhub.backend.oee.infrastructure.PlannedDowntimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeletePlannedDowntimeUseCase {

    private final PlannedDowntimeRepository plannedDowntimeRepository;

    public DeletePlannedDowntimeUseCase(PlannedDowntimeRepository plannedDowntimeRepository) {
        this.plannedDowntimeRepository = plannedDowntimeRepository;
    }

    @Transactional
    public void execute(UUID id) {
        if (!plannedDowntimeRepository.existsById(id)) {
            throw new PlannedDowntimeNotFoundException(id);
        }
        plannedDowntimeRepository.deleteById(id);
    }
}
