package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.ShiftResponse;
import com.industrialhub.backend.common.application.dto.UpdateShiftRequest;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.Shift;
import com.industrialhub.backend.common.domain.ShiftNotFoundException;
import com.industrialhub.backend.common.infrastructure.ShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UpdateShiftUseCase {

    private final ShiftRepository shiftRepository;
    private final ShiftResolverService shiftResolverService;
    private final AuditService auditService;

    public UpdateShiftUseCase(ShiftRepository shiftRepository,
                               ShiftResolverService shiftResolverService,
                               AuditService auditService) {
        this.shiftRepository = shiftRepository;
        this.shiftResolverService = shiftResolverService;
        this.auditService = auditService;
    }

    @Transactional
    public ShiftResponse execute(UUID id, UpdateShiftRequest request, String updatedBy) {
        Shift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new ShiftNotFoundException(id));

        // Validação: turno não-noturno exige endTime > startTime
        if (!request.overnight() && !request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException(
                    "endTime deve ser posterior a startTime para turno não-noturno");
        }

        // Verificar sobreposição com turnos ativos (excluindo o próprio turno)
        List<Shift> activeShifts = shiftRepository.findAllByActiveTrueOrderByStartTime();
        Shift candidate = Shift.builder()
                .id(id)
                .name(request.name())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .overnight(request.overnight())
                .build();

        for (Shift existing : activeShifts) {
            if (existing.getId().equals(id)) {
                continue; // Excluir o próprio turno da verificação
            }
            if (shiftResolverService.overlaps(existing, candidate)) {
                throw new IllegalStateException(
                        "Turno sobrepõe turno existente: " + existing.getName());
            }
        }

        shift.setName(request.name());
        shift.setStartTime(request.startTime());
        shift.setEndTime(request.endTime());
        shift.setOvernight(request.overnight());

        Shift saved = shiftRepository.save(shift);

        auditService.log(updatedBy, AuditAction.SHIFT_UPDATED, "Shift", id.toString(),
                Map.of("name", request.name(),
                       "startTime", request.startTime().toString(),
                       "endTime", request.endTime().toString()));

        return ShiftResponse.from(saved);
    }
}
