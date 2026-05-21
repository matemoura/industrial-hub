package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
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
public class DeactivatePlantUseCase {

    private final PlantRepository plantRepository;
    private final AuditService auditService;

    @Transactional
    public void execute(UUID id, String deactivatedBy) {
        Plant plant = plantRepository.findById(id)
            .orElseThrow(() -> new PlantNotFoundException(id));

        plant.setActive(false);
        plantRepository.save(plant);

        auditService.log(deactivatedBy, AuditAction.PLANT_DEACTIVATED, "Plant",
            id.toString(),
            Map.of("code", plant.getCode(), "name", plant.getName()));
    }
}
