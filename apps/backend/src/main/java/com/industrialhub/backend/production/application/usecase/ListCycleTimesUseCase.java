package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.CycleTimeResponse;
import com.industrialhub.backend.production.infrastructure.CycleTimeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ListCycleTimesUseCase {

    private final CycleTimeRepository cycleTimeRepository;

    public ListCycleTimesUseCase(CycleTimeRepository cycleTimeRepository) {
        this.cycleTimeRepository = cycleTimeRepository;
    }

    public List<CycleTimeResponse> execute(UUID productId) {
        if (productId != null) {
            return cycleTimeRepository.findByProductIdOrderByEffectiveDateDesc(productId)
                    .stream()
                    .map(CycleTimeResponse::from)
                    .toList();
        }
        return cycleTimeRepository.findAll().stream()
                .map(CycleTimeResponse::from)
                .toList();
    }
}
