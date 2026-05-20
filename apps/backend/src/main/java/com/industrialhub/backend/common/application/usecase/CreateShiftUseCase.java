package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.CreateShiftRequest;
import com.industrialhub.backend.common.application.dto.ShiftResponse;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.Shift;
import com.industrialhub.backend.common.infrastructure.ShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class CreateShiftUseCase {

    private final ShiftRepository shiftRepository;
    private final ShiftResolverService shiftResolverService;
    private final AuditService auditService;

    public CreateShiftUseCase(ShiftRepository shiftRepository,
                               ShiftResolverService shiftResolverService,
                               AuditService auditService) {
        this.shiftRepository = shiftRepository;
        this.shiftResolverService = shiftResolverService;
        this.auditService = auditService;
    }

    @Transactional
    public ShiftResponse execute(CreateShiftRequest request, String addedBy) {
        // Validação: turno não-noturno exige endTime > startTime
        if (!request.overnight() && !request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException(
                    "endTime deve ser posterior a startTime para turno não-noturno");
        }

        // Verificar sobreposição com turnos ativos
        List<Shift> activeShifts = shiftRepository.findAllByActiveTrueOrderByStartTime();
        Shift candidate = Shift.builder()
                .name(request.name())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .overnight(request.overnight())
                .build();

        for (Shift existing : activeShifts) {
            if (shiftResolverService.overlaps(existing, candidate)) {
                throw new IllegalStateException(
                        "Turno sobrepõe turno existente: " + existing.getName());
            }
        }

        Shift saved = shiftRepository.save(candidate);

        auditService.log(addedBy, AuditAction.SHIFT_CREATED, "Shift", saved.getId().toString(),
                Map.of("name", saved.getName(),
                       "startTime", saved.getStartTime().toString(),
                       "endTime", saved.getEndTime().toString(),
                       "overnight", String.valueOf(saved.isOvernight())));

        return ShiftResponse.from(saved);
    }
}
