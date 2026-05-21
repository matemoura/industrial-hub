package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.CreatePlantRequest;
import com.industrialhub.backend.common.application.dto.PlantResponse;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.Plant;
import com.industrialhub.backend.common.domain.PlantDuplicateCodeException;
import com.industrialhub.backend.common.infrastructure.PlantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CreatePlantUseCase {

    private final PlantRepository plantRepository;
    private final AuditService auditService;

    @Transactional
    public PlantResponse execute(CreatePlantRequest request, String createdBy) {
        if (plantRepository.existsByCode(request.code().toUpperCase())) {
            throw new PlantDuplicateCodeException(request.code());
        }

        Plant plant = Plant.builder()
            .code(request.code().toUpperCase())
            .name(request.name())
            .address(request.address())
            .timezone(request.timezone())
            .active(true)
            .isDefault(false)
            .createdAt(LocalDateTime.now())
            .build();

        Plant saved = plantRepository.save(plant);

        auditService.log(createdBy, AuditAction.PLANT_CREATED, "Plant",
            saved.getId().toString(),
            Map.of("code", saved.getCode(), "name", saved.getName()));

        return PlantResponse.from(saved);
    }
}
