package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.PlantResponse;
import com.industrialhub.backend.common.infrastructure.PlantRepository;
import com.industrialhub.backend.common.infrastructure.UserPlantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListUserPlantsUseCase {

    private final UserPlantRepository userPlantRepository;
    private final PlantRepository plantRepository;

    @Transactional(readOnly = true)
    public List<PlantResponse> execute(UUID userId) {
        List<UUID> plantIds = userPlantRepository.findPlantIdsByUserId(userId);
        return plantRepository.findAllById(plantIds).stream()
            .map(PlantResponse::from)
            .toList();
    }
}
