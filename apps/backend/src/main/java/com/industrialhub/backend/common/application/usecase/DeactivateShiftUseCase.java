package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.domain.Shift;
import com.industrialhub.backend.common.domain.ShiftNotFoundException;
import com.industrialhub.backend.common.infrastructure.ShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeactivateShiftUseCase {

    private final ShiftRepository shiftRepository;

    public DeactivateShiftUseCase(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    @Transactional
    public void execute(UUID id) {
        Shift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new ShiftNotFoundException(id));
        shift.setActive(false);
        shiftRepository.save(shift);
    }
}
