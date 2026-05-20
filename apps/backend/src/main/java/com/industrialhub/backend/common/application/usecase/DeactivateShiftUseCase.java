package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.Shift;
import com.industrialhub.backend.common.domain.ShiftNotFoundException;
import com.industrialhub.backend.common.infrastructure.ShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class DeactivateShiftUseCase {

    private final ShiftRepository shiftRepository;
    private final AuditService auditService;

    public DeactivateShiftUseCase(ShiftRepository shiftRepository,
                                   AuditService auditService) {
        this.shiftRepository = shiftRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void execute(UUID id, String deactivatedBy) {
        Shift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new ShiftNotFoundException(id));
        shift.setActive(false);
        shiftRepository.save(shift);

        auditService.log(deactivatedBy, AuditAction.SHIFT_DEACTIVATED, "Shift", id.toString(),
                Map.of("name", shift.getName()));
    }
}
