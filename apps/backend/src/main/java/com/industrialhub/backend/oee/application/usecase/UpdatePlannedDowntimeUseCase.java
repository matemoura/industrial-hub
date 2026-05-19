package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.maintenance.domain.EquipmentNotFoundException;
import com.industrialhub.backend.oee.application.dto.PlannedDowntimeResponse;
import com.industrialhub.backend.oee.application.dto.UpdatePlannedDowntimeRequest;
import com.industrialhub.backend.oee.domain.PlannedDowntime;
import com.industrialhub.backend.oee.domain.PlannedDowntimeNotFoundException;
import com.industrialhub.backend.oee.infrastructure.PlannedDowntimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class UpdatePlannedDowntimeUseCase {

    private final PlannedDowntimeRepository plannedDowntimeRepository;
    private final EquipmentRepository equipmentRepository;
    private final AuditService auditService;

    public UpdatePlannedDowntimeUseCase(PlannedDowntimeRepository plannedDowntimeRepository,
                                        EquipmentRepository equipmentRepository,
                                        AuditService auditService) {
        this.plannedDowntimeRepository = plannedDowntimeRepository;
        this.equipmentRepository = equipmentRepository;
        this.auditService = auditService;
    }

    @Transactional
    public PlannedDowntimeResponse execute(UUID id, UpdatePlannedDowntimeRequest request, String username) {
        PlannedDowntime downtime = plannedDowntimeRepository.findById(id)
                .orElseThrow(() -> new PlannedDowntimeNotFoundException(id));

        Equipment equipment = null;
        if (request.equipmentId() != null) {
            equipment = equipmentRepository.findByIdAndActiveTrue(request.equipmentId())
                    .orElseThrow(() -> new EquipmentNotFoundException(request.equipmentId()));
        }

        if (!request.endAt().isAfter(request.startAt())) {
            throw new IllegalArgumentException("endAt deve ser posterior a startAt");
        }
        long minutes = java.time.temporal.ChronoUnit.MINUTES.between(request.startAt(), request.endAt());
        if (minutes > 1440) {
            throw new IllegalArgumentException("A duração da parada não pode exceder 24 horas (1440 minutos)");
        }

        downtime.setEquipment(equipment);
        downtime.setStartAt(request.startAt());
        downtime.setEndAt(request.endAt());
        downtime.setReason(request.reason());
        downtime.setDescription(request.description());

        PlannedDowntime saved = plannedDowntimeRepository.save(downtime);

        auditService.log(username, AuditAction.DOWNTIME_UPDATED, "PlannedDowntime",
                saved.getId().toString(),
                Map.of(
                        "date", saved.getDate() != null ? saved.getDate().toString() : "",
                        "durationMinutes", String.valueOf(saved.getDurationMinutes()),
                        "reason", saved.getReason().name(),
                        "equipmentId", saved.getEquipment() != null
                                ? saved.getEquipment().getId().toString()
                                : "plant-wide"
                ));

        return PlannedDowntimeResponse.from(saved);
    }
}
