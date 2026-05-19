package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.oee.domain.PlannedDowntime;
import com.industrialhub.backend.oee.domain.PlannedDowntimeNotFoundException;
import com.industrialhub.backend.oee.infrastructure.PlannedDowntimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class DeletePlannedDowntimeUseCase {

    private final PlannedDowntimeRepository plannedDowntimeRepository;
    private final AuditService auditService;

    public DeletePlannedDowntimeUseCase(PlannedDowntimeRepository plannedDowntimeRepository,
                                        AuditService auditService) {
        this.plannedDowntimeRepository = plannedDowntimeRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void execute(UUID id, String username) {
        PlannedDowntime toDelete = plannedDowntimeRepository.findById(id)
                .orElseThrow(() -> new PlannedDowntimeNotFoundException(id));

        plannedDowntimeRepository.delete(toDelete);

        auditService.log(username, AuditAction.DOWNTIME_DELETED, "PlannedDowntime",
                id.toString(),
                Map.of(
                        "date", toDelete.getDate() != null ? toDelete.getDate().toString() : "",
                        "durationMinutes", String.valueOf(toDelete.getDurationMinutes()),
                        "reason", toDelete.getReason().name()
                ));
    }
}
