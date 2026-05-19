package com.industrialhub.backend.oee;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.oee.application.usecase.DeletePlannedDowntimeUseCase;
import com.industrialhub.backend.oee.domain.DowntimeReason;
import com.industrialhub.backend.oee.domain.PlannedDowntime;
import com.industrialhub.backend.oee.domain.PlannedDowntimeNotFoundException;
import com.industrialhub.backend.oee.infrastructure.PlannedDowntimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeletePlannedDowntimeUseCaseTest {

    @Mock
    private PlannedDowntimeRepository plannedDowntimeRepository;

    @Mock
    private AuditService auditService;

    private DeletePlannedDowntimeUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeletePlannedDowntimeUseCase(plannedDowntimeRepository, auditService);
    }

    @Test
    void shouldDeleteDowntime_andCallAudit() {
        // Arrange
        UUID id = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.of(2026, 5, 20, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 20, 10, 0);

        PlannedDowntime downtime = PlannedDowntime.builder()
                .id(id)
                .equipment(null)
                .startAt(start)
                .endAt(end)
                .reason(DowntimeReason.HOLIDAY)
                .description("Feriado")
                .registeredBy("supervisor")
                .registeredAt(LocalDateTime.now())
                .build();

        when(plannedDowntimeRepository.findById(id)).thenReturn(Optional.of(downtime));

        // Act
        useCase.execute(id, "supervisor");

        // Assert
        verify(plannedDowntimeRepository).delete(downtime);
        verify(auditService).log(eq("supervisor"), eq(AuditAction.DOWNTIME_DELETED),
                eq("PlannedDowntime"), eq(id.toString()), any());
    }

    @Test
    void shouldThrow404_whenDowntimeNotFound_andNotCallAudit() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        when(plannedDowntimeRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(nonExistentId, "supervisor"))
                .isInstanceOf(PlannedDowntimeNotFoundException.class);

        verify(plannedDowntimeRepository, never()).delete(any());
        // Auditoria NÃO deve ser chamada quando id não existe
        verify(auditService, never()).log(any(), any(), any(), any(String.class), any());
    }
}
