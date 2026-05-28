package com.industrialhub.backend.production;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.production.application.dto.CreateSterilizationLoadRequest;
import com.industrialhub.backend.production.application.dto.SterilizationLoadResponse;
import com.industrialhub.backend.production.application.usecase.CreateSterilizationLoadUseCase;
import com.industrialhub.backend.production.domain.LoadStatus;
import com.industrialhub.backend.production.domain.SterilizationLoad;
import com.industrialhub.backend.production.domain.SterilizationMethod;
import com.industrialhub.backend.production.infrastructure.SterilizationLoadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateSterilizationLoadUseCaseTest {

    @Mock SterilizationLoadRepository loadRepository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock AuditService auditService;
    @InjectMocks CreateSterilizationLoadUseCase useCase;

    @Test
    void shouldGenerateLoadNumberSequentially() {
        when(loadRepository.nextSequenceForYear(anyInt())).thenReturn(1);
        when(loadRepository.save(any())).thenAnswer(inv -> {
            SterilizationLoad sl = inv.getArgument(0);
            sl.setId(UUID.randomUUID());
            return sl;
        });

        var request = new CreateSterilizationLoadRequest(null, SterilizationMethod.EO_GAS,
                LocalDate.now().plusDays(7), null, null);
        SterilizationLoadResponse result = useCase.execute(request, "supervisor1");

        assertThat(result.loadNumber()).matches("CARGA-\\d{4}-001");
        assertThat(result.status()).isEqualTo(LoadStatus.OPEN);
        assertThat(result.method()).isEqualTo(SterilizationMethod.EO_GAS);
        verify(auditService).log(eq("supervisor1"), any(), anyString(), anyString(), anyMap());
    }

    @Test
    void shouldCreateLoadWithNullSterilizerWhenNotProvided() {
        when(loadRepository.nextSequenceForYear(anyInt())).thenReturn(3);
        when(loadRepository.save(any())).thenAnswer(inv -> {
            SterilizationLoad sl = inv.getArgument(0);
            sl.setId(UUID.randomUUID());
            return sl;
        });

        var request = new CreateSterilizationLoadRequest(null, SterilizationMethod.GAMMA,
                null, "LOTE-2026-001", "Teste ANVISA");
        SterilizationLoadResponse result = useCase.execute(request, "admin1");

        assertThat(result.loadNumber()).matches("CARGA-\\d{4}-003");
        assertThat(result.sterilizerName()).isNull();
        assertThat(result.batchCode()).isEqualTo("LOTE-2026-001");
        verify(equipmentRepository, never()).findByIdAndActiveTrue(any());
    }

    @Test
    void shouldPadSequenceNumberToThreeDigits() {
        when(loadRepository.nextSequenceForYear(anyInt())).thenReturn(42);
        when(loadRepository.save(any())).thenAnswer(inv -> {
            SterilizationLoad sl = inv.getArgument(0);
            sl.setId(UUID.randomUUID());
            return sl;
        });

        var request = new CreateSterilizationLoadRequest(null, SterilizationMethod.STEAM,
                null, null, null);
        SterilizationLoadResponse result = useCase.execute(request, "supervisor1");

        assertThat(result.loadNumber()).matches("CARGA-\\d{4}-042");
    }
}
