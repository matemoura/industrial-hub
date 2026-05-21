package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.PlantResponse;
import com.industrialhub.backend.common.application.dto.UpdatePlantRequest;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.Plant;
import com.industrialhub.backend.common.domain.PlantNotFoundException;
import com.industrialhub.backend.common.infrastructure.PlantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdatePlantUseCase {

    private final PlantRepository plantRepository;
    private final AuditService auditService;

    @Transactional
    public PlantResponse execute(UUID id, UpdatePlantRequest request, String updatedBy) {
        Plant plant = plantRepository.findById(id)
            .orElseThrow(() -> new PlantNotFoundException(id));

        plant.setName(request.name());
        plant.setAddress(request.address());
        plant.setTimezone(request.timezone());

        Plant saved = plantRepository.save(plant);

        auditService.log(updatedBy, AuditAction.PLANT_UPDATED, "Plant",
            id.toString(),
            Map.of("name", request.name()));

        return PlantResponse.from(saved);
    }
}
