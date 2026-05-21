package com.industrialhub.backend.common.plant;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.CreatePlantRequest;
import com.industrialhub.backend.common.application.dto.PlantResponse;
import com.industrialhub.backend.common.application.usecase.CreatePlantUseCase;
import com.industrialhub.backend.common.domain.Plant;
import com.industrialhub.backend.common.domain.PlantDuplicateCodeException;
import com.industrialhub.backend.common.infrastructure.PlantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatePlantUseCaseTest {

    @Mock PlantRepository plantRepository;
    @Mock AuditService auditService;
    @InjectMocks CreatePlantUseCase useCase;

    @Test
    void create_success() {
        CreatePlantRequest request = new CreatePlantRequest("SP01", "Unidade São Paulo",
            "Rua das Flores, 100", "America/Sao_Paulo");

        when(plantRepository.existsByCode("SP01")).thenReturn(false);

        Plant saved = Plant.builder()
            .id(UUID.randomUUID())
            .code("SP01")
            .name("Unidade São Paulo")
            .address("Rua das Flores, 100")
            .timezone("America/Sao_Paulo")
            .active(true)
            .isDefault(false)
            .createdAt(LocalDateTime.now())
            .build();
        when(plantRepository.save(any())).thenReturn(saved);

        PlantResponse response = useCase.execute(request, "admin");

        assertThat(response.code()).isEqualTo("SP01");
        assertThat(response.name()).isEqualTo("Unidade São Paulo");
        assertThat(response.active()).isTrue();
        assertThat(response.isDefault()).isFalse();
        verify(auditService).log(eq("admin"), any(), eq("Plant"), any(String.class), any());
    }

    @Test
    void create_duplicateCode_throwsException() {
        CreatePlantRequest request = new CreatePlantRequest("SP01", "Unidade São Paulo", null, null);

        when(plantRepository.existsByCode("SP01")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(request, "admin"))
            .isInstanceOf(PlantDuplicateCodeException.class)
            .hasMessageContaining("SP01");

        verify(plantRepository, never()).save(any());
    }

    @Test
    void create_normalizesCodeToUppercase() {
        CreatePlantRequest request = new CreatePlantRequest("sp01", "Unidade São Paulo", null, null);

        when(plantRepository.existsByCode("SP01")).thenReturn(false);

        Plant saved = Plant.builder()
            .id(UUID.randomUUID())
            .code("SP01")
            .name("Unidade São Paulo")
            .active(true)
            .isDefault(false)
            .createdAt(LocalDateTime.now())
            .build();
        when(plantRepository.save(any())).thenReturn(saved);

        PlantResponse response = useCase.execute(request, "admin");

        assertThat(response.code()).isEqualTo("SP01");
        // Verify code was uppercased before checking existence
        verify(plantRepository).existsByCode("SP01");
    }
}
