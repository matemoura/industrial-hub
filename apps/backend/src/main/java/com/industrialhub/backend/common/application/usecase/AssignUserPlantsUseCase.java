package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.AssignUserPlantsRequest;
import com.industrialhub.backend.common.application.dto.PlantResponse;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.auth.domain.UserNotFoundException;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.Plant;
import com.industrialhub.backend.common.domain.PlantNotFoundException;
import com.industrialhub.backend.common.domain.UserPlant;
import com.industrialhub.backend.common.infrastructure.PlantRepository;
import com.industrialhub.backend.common.infrastructure.UserPlantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignUserPlantsUseCase {

    private final UserRepository userRepository;
    private final PlantRepository plantRepository;
    private final UserPlantRepository userPlantRepository;
    private final AuditService auditService;

    @Transactional
    public List<PlantResponse> execute(UUID userId, AssignUserPlantsRequest request, String assignedBy) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        // Batch fetch — only active plants (eliminates N+1 queries and rejects inactive plants)
        List<Plant> plants = plantRepository.findAllByIdInAndActiveTrue(request.plantIds());

        // Verify all requested plants were found and active
        // A missing ID may be either non-existent or inactive — both treated as PlantNotFoundException
        if (plants.size() != request.plantIds().size()) {
            Set<UUID> foundIds = plants.stream().map(Plant::getId).collect(Collectors.toSet());
            UUID missingId = request.plantIds().stream()
                .filter(id -> !foundIds.contains(id))
                .findFirst()
                .orElseThrow(); // cannot happen given the size check above
            throw new PlantNotFoundException(missingId);
        }

        // Remove all existing associations and create new ones in bulk
        userPlantRepository.deleteByUserId(userId);

        List<UserPlant> associations = plants.stream()
            .map(p -> UserPlant.builder().user(user).plant(p).build())
            .toList();
        userPlantRepository.saveAll(associations);

        auditService.log(assignedBy, AuditAction.USER_PLANT_ASSIGNED, "User",
            userId.toString(),
            Map.of("plantIds", request.plantIds().toString()));

        return plants.stream().map(PlantResponse::from).toList();
    }
}
