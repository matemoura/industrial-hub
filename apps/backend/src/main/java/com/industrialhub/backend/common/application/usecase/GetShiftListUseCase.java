package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.ShiftResponse;
import com.industrialhub.backend.common.infrastructure.ShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetShiftListUseCase {

    private final ShiftRepository shiftRepository;

    public GetShiftListUseCase(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> execute() {
        return shiftRepository.findAllByActiveTrueOrderByStartTime()
                .stream()
                .map(ShiftResponse::from)
                .toList();
    }
}
