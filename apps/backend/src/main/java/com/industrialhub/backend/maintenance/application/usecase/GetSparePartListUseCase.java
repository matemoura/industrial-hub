package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.SparePartResponse;
import com.industrialhub.backend.maintenance.domain.SparePartNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.SparePartRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class GetSparePartListUseCase {

    private final SparePartRepository sparePartRepository;

    public GetSparePartListUseCase(SparePartRepository sparePartRepository) {
        this.sparePartRepository = sparePartRepository;
    }

    public List<SparePartResponse> execute(String category, boolean belowMin) {
        return sparePartRepository.findActiveWithFilters(category, belowMin)
                .stream()
                .map(SparePartResponse::from)
                .toList();
    }

    public SparePartResponse executeById(UUID id) {
        return sparePartRepository.findById(id)
                .map(SparePartResponse::from)
                .orElseThrow(() -> new SparePartNotFoundException(id));
    }
}
