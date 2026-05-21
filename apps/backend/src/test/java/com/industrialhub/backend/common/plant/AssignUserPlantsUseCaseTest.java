package com.industrialhub.backend.common.plant;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.AssignUserPlantsRequest;
import com.industrialhub.backend.common.application.dto.PlantResponse;
import com.industrialhub.backend.common.application.usecase.AssignUserPlantsUseCase;
import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.domain.UserNotFoundException;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.domain.Plant;
import com.industrialhub.backend.common.domain.PlantNotFoundException;
import com.industrialhub.backend.common.domain.UserPlant;
import com.industrialhub.backend.common.infrastructure.PlantRepository;
import com.industrialhub.backend.common.infrastructure.UserPlantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignUserPlantsUseCaseTest {

    @Mock UserRepository userRepository;
    @Mock PlantRepository plantRepository;
    @Mock UserPlantRepository userPlantRepository;
    @Mock AuditService auditService;
    @InjectMocks AssignUserPlantsUseCase useCase;

    // --- MF-S23-02: batch fetch eliminates N+1 ---

    @Test
    void assign_success_usesBatchFetch() {
        UUID userId = UUID.randomUUID();
        UUID plantId1 = UUID.randomUUID();
        UUID plantId2 = UUID.randomUUID();

        User user = User.builder()
            .id(userId).username("operator").role(Role.OPERATOR).active(true).build();

        Plant plant1 = Plant.builder()
            .id(plantId1).code("SP01").name("São Paulo")
            .active(true).isDefault(false).createdAt(LocalDateTime.now()).build();
        Plant plant2 = Plant.builder()
            .id(plantId2).code("RJ01").name("Rio de Janeiro")
            .active(true).isDefault(false).createdAt(LocalDateTime.now()).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(plantRepository.findAllByIdInAndActiveTrue(List.of(plantId1, plantId2))).thenReturn(List.of(plant1, plant2));
        when(userPlantRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        AssignUserPlantsRequest request = new AssignUserPlantsRequest(List.of(plantId1, plantId2));
        List<PlantResponse> result = useCase.execute(userId, request, "admin");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PlantResponse::code).containsExactlyInAnyOrder("SP01", "RJ01");

        // Verify batch operations — no N+1
        verify(plantRepository).findAllByIdInAndActiveTrue(List.of(plantId1, plantId2));
        verify(userPlantRepository).deleteByUserId(userId);
        verify(userPlantRepository).saveAll(anyList());
        verify(userPlantRepository, never()).save(any(UserPlant.class)); // no individual saves
        verify(auditService).log(eq("admin"), any(), eq("User"), eq(userId.toString()), any());
    }

    @Test
    void assign_emptyList_removesAllAssociations() {
        UUID userId = UUID.randomUUID();

        User user = User.builder()
            .id(userId).username("operator").role(Role.OPERATOR).active(true).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(plantRepository.findAllByIdInAndActiveTrue(List.of())).thenReturn(List.of());
        when(userPlantRepository.saveAll(List.of())).thenReturn(List.of());

        AssignUserPlantsRequest request = new AssignUserPlantsRequest(List.of());
        List<PlantResponse> result = useCase.execute(userId, request, "admin");

        assertThat(result).isEmpty();
        verify(userPlantRepository).deleteByUserId(userId);
        verify(userPlantRepository).saveAll(List.of());
        verify(userPlantRepository, never()).save(any());
    }

    @Test
    void assign_userNotFound_throwsException() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AssignUserPlantsRequest request = new AssignUserPlantsRequest(List.of());

        assertThatThrownBy(() -> useCase.execute(userId, request, "admin"))
            .isInstanceOf(UserNotFoundException.class);
    }

    // --- MF-S23-02: missing plant detected in batch ---

    @Test
    void assign_plantNotFound_throwsPlantNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID existingPlantId = UUID.randomUUID();
        UUID missingPlantId = UUID.randomUUID();

        User user = User.builder()
            .id(userId).username("operator").role(Role.OPERATOR).active(true).build();

        Plant existing = Plant.builder()
            .id(existingPlantId).code("SP01").name("São Paulo")
            .active(true).isDefault(false).createdAt(LocalDateTime.now()).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        // Only 1 of 2 plants found — batch returns partial result
        when(plantRepository.findAllByIdInAndActiveTrue(anyList())).thenReturn(List.of(existing));

        AssignUserPlantsRequest request = new AssignUserPlantsRequest(
            List.of(existingPlantId, missingPlantId));

        assertThatThrownBy(() -> useCase.execute(userId, request, "admin"))
            .isInstanceOf(PlantNotFoundException.class);

        verify(userPlantRepository, never()).deleteByUserId(any());
        verify(userPlantRepository, never()).saveAll(anyList());
    }

    // --- MF-S23-02: inactive plant rejected ---

    @Test
    void assign_inactivePlant_throwsPlantNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID inactivePlantId = UUID.randomUUID();

        User user = User.builder()
            .id(userId).username("operator").role(Role.OPERATOR).active(true).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        // findAllByIdInAndActiveTrue returns empty because plant is inactive
        when(plantRepository.findAllByIdInAndActiveTrue(List.of(inactivePlantId))).thenReturn(List.of());

        AssignUserPlantsRequest request = new AssignUserPlantsRequest(List.of(inactivePlantId));

        assertThatThrownBy(() -> useCase.execute(userId, request, "admin"))
            .isInstanceOf(PlantNotFoundException.class);

        verify(userPlantRepository, never()).deleteByUserId(any());
    }
}
