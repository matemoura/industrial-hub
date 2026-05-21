package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.PlantResponse;
import com.industrialhub.backend.common.infrastructure.PlantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetPlantListUseCase {

    private final PlantRepository plantRepository;

    @Transactional(readOnly = true)
    public List<PlantResponse> execute() {
        return plantRepository.findByActiveTrueOrderByNameAsc()
            .stream()
            .map(PlantResponse::from)
            .toList();
    }
}
