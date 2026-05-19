package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.maintenance.domain.EquipmentNotFoundException;
import com.industrialhub.backend.oee.application.dto.CreatePlannedDowntimeRequest;
import com.industrialhub.backend.oee.application.dto.PlannedDowntimeResponse;
import com.industrialhub.backend.oee.domain.PlannedDowntime;
import com.industrialhub.backend.oee.infrastructure.PlannedDowntimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class CreatePlannedDowntimeUseCase {

    private final PlannedDowntimeRepository plannedDowntimeRepository;
    private final EquipmentRepository equipmentRepository;

    public CreatePlannedDowntimeUseCase(PlannedDowntimeRepository plannedDowntimeRepository,
                                        EquipmentRepository equipmentRepository) {
        this.plannedDowntimeRepository = plannedDowntimeRepository;
        this.equipmentRepository = equipmentRepository;
    }

    @Transactional
    public PlannedDowntimeResponse execute(CreatePlannedDowntimeRequest request, String username) {
        validateInterval(request.startAt(), request.endAt());

        Equipment equipment = null;
        if (request.equipmentId() != null) {
            equipment = equipmentRepository.findByIdAndActiveTrue(request.equipmentId())
                    .orElseThrow(() -> new EquipmentNotFoundException(request.equipmentId()));
        }

        PlannedDowntime downtime = PlannedDowntime.builder()
                .equipment(equipment)
                .startAt(request.startAt())
                .endAt(request.endAt())
                .reason(request.reason())
                .description(request.description())
                .registeredBy(username)
                .registeredAt(LocalDateTime.now())
                .build();

        PlannedDowntime saved = plannedDowntimeRepository.save(downtime);
        return PlannedDowntimeResponse.from(saved);
    }

    private void validateInterval(LocalDateTime startAt, LocalDateTime endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("endAt deve ser posterior a startAt");
        }
        long minutes = ChronoUnit.MINUTES.between(startAt, endAt);
        if (minutes > 1440) {
            throw new IllegalArgumentException("A duração da parada não pode exceder 24 horas (1440 minutos)");
        }
    }
}
