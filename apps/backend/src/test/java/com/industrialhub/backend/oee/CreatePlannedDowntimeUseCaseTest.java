package com.industrialhub.backend.oee;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.oee.application.dto.CreatePlannedDowntimeRequest;
import com.industrialhub.backend.oee.application.dto.PlannedDowntimeResponse;
import com.industrialhub.backend.oee.application.usecase.CreatePlannedDowntimeUseCase;
import com.industrialhub.backend.oee.domain.DowntimeReason;
import com.industrialhub.backend.oee.domain.PlannedDowntime;
import com.industrialhub.backend.oee.infrastructure.PlannedDowntimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatePlannedDowntimeUseCaseTest {

    @Mock
    private PlannedDowntimeRepository plannedDowntimeRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private AuditService auditService;

    private CreatePlannedDowntimeUseCase useCase;

    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 5, 20, 8, 0);
    private static final LocalDateTime END_AT   = LocalDateTime.of(2026, 5, 20, 16, 0);

    @BeforeEach
    void setUp() {
        useCase = new CreatePlannedDowntimeUseCase(plannedDowntimeRepository, equipmentRepository, auditService);
    }

    @Test
    void shouldCreateDowntime_withNullEquipment_plantWide() {
        // Arrange
        CreatePlannedDowntimeRequest request = new CreatePlannedDowntimeRequest(
                null, START_AT, END_AT, DowntimeReason.HOLIDAY, "Feriado nacional"
        );

        PlannedDowntime saved = PlannedDowntime.builder()
                .id(UUID.randomUUID())
                .equipment(null)
                .startAt(START_AT)
                .endAt(END_AT)
                .reason(DowntimeReason.HOLIDAY)
                .description("Feriado nacional")
                .registeredBy("supervisor")
                .registeredAt(LocalDateTime.now())
                .build();

        when(plannedDowntimeRepository.save(any(PlannedDowntime.class))).thenReturn(saved);

        // Act
        PlannedDowntimeResponse response = useCase.execute(request, "supervisor");

        // Assert
        assertThat(response.equipmentId()).isNull();
        assertThat(response.equipmentCode()).isNull();
        assertThat(response.equipmentName()).isNull();
        assertThat(response.startAt()).isEqualTo(START_AT);
        assertThat(response.endAt()).isEqualTo(END_AT);
        assertThat(response.durationMinutes()).isEqualTo(480);
        assertThat(response.reason()).isEqualTo(DowntimeReason.HOLIDAY);
        assertThat(response.description()).isEqualTo("Feriado nacional");
        assertThat(response.registeredBy()).isEqualTo("supervisor");

        verify(equipmentRepository, never()).findByIdAndActiveTrue(any());

        // Auditoria deve ser chamada no happy path
        verify(auditService).log(eq("supervisor"), eq(AuditAction.DOWNTIME_CREATED),
                eq("PlannedDowntime"), any(String.class), any());
    }

    @Test
    void shouldCreateDowntime_withExistingEquipment() {
        // Arrange
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = Equipment.builder()
                .id(equipmentId)
                .code("EQ-001")
                .name("Torno CNC")
                .build();

        LocalDateTime start = LocalDateTime.of(2026, 5, 20, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 5, 20, 12, 0);

        CreatePlannedDowntimeRequest request = new CreatePlannedDowntimeRequest(
                equipmentId, start, end, DowntimeReason.PREVENTIVE_MAINTENANCE, null
        );

        PlannedDowntime saved = PlannedDowntime.builder()
                .id(UUID.randomUUID())
                .equipment(equipment)
                .startAt(start)
                .endAt(end)
                .reason(DowntimeReason.PREVENTIVE_MAINTENANCE)
                .registeredBy("supervisor")
                .registeredAt(LocalDateTime.now())
                .build();

        when(equipmentRepository.findByIdAndActiveTrue(equipmentId))
                .thenReturn(Optional.of(equipment));
        when(plannedDowntimeRepository.save(any(PlannedDowntime.class))).thenReturn(saved);

        // Act
        PlannedDowntimeResponse response = useCase.execute(request, "supervisor");

        // Assert
        assertThat(response.equipmentId()).isEqualTo(equipmentId);
        assertThat(response.equipmentCode()).isEqualTo("EQ-001");
        assertThat(response.equipmentName()).isEqualTo("Torno CNC");
        assertThat(response.durationMinutes()).isEqualTo(120);
        assertThat(response.reason()).isEqualTo(DowntimeReason.PREVENTIVE_MAINTENANCE);

        ArgumentCaptor<PlannedDowntime> captor = ArgumentCaptor.forClass(PlannedDowntime.class);
        verify(plannedDowntimeRepository).save(captor.capture());
        assertThat(captor.getValue().getEquipment()).isEqualTo(equipment);

        verify(auditService).log(eq("supervisor"), eq(AuditAction.DOWNTIME_CREATED),
                eq("PlannedDowntime"), any(String.class), any());
    }

    @Test
    void shouldThrow404_whenEquipmentNotFound() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        CreatePlannedDowntimeRequest request = new CreatePlannedDowntimeRequest(
                unknownId, START_AT, END_AT, DowntimeReason.SCHEDULED_SETUP, null
        );

        when(equipmentRepository.findByIdAndActiveTrue(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(request, "supervisor"))
                .isInstanceOf(EquipmentNotFoundException.class);

        verify(plannedDowntimeRepository, never()).save(any());
        // Auditoria NÃO deve ser chamada quando equipamento não é encontrado
        verify(auditService, never()).log(any(), any(), any(), any(String.class), any());
    }

    @Test
    void shouldPersistRegisteredByFromUsername() {
        // Arrange
        LocalDateTime start = LocalDateTime.of(2026, 5, 20, 14, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 5, 20, 15, 0);

        CreatePlannedDowntimeRequest request = new CreatePlannedDowntimeRequest(
                null, start, end, DowntimeReason.OTHER, "Treinamento"
        );

        PlannedDowntime saved = PlannedDowntime.builder()
                .id(UUID.randomUUID())
                .startAt(start)
                .endAt(end)
                .reason(DowntimeReason.OTHER)
                .description("Treinamento")
                .registeredBy("joao.silva")
                .registeredAt(LocalDateTime.now())
                .build();

        when(plannedDowntimeRepository.save(any(PlannedDowntime.class))).thenReturn(saved);

        // Act
        PlannedDowntimeResponse response = useCase.execute(request, "joao.silva");

        // Assert
        ArgumentCaptor<PlannedDowntime> captor = ArgumentCaptor.forClass(PlannedDowntime.class);
        verify(plannedDowntimeRepository).save(captor.capture());
        assertThat(captor.getValue().getRegisteredBy()).isEqualTo("joao.silva");
        assertThat(captor.getValue().getRegisteredAt()).isNotNull();
    }

    @Test
    void shouldThrow404_whenEquipmentIsInactive_andNotCallAudit() {
        // Arrange
        UUID inactiveEquipmentId = UUID.randomUUID();
        CreatePlannedDowntimeRequest request = new CreatePlannedDowntimeRequest(
                inactiveEquipmentId, START_AT, END_AT, DowntimeReason.PREVENTIVE_MAINTENANCE, null
        );

        when(equipmentRepository.findByIdAndActiveTrue(inactiveEquipmentId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(request, "admin"))
                .isInstanceOf(EquipmentNotFoundException.class);

        // Auditoria NÃO deve ser chamada quando equipamento está inativo
        verify(auditService, never()).log(any(), any(), any(), any(String.class), any());
    }

    @Test
    void shouldThrow_whenEndAtBeforeStartAt() {
        // Arrange
        CreatePlannedDowntimeRequest request = new CreatePlannedDowntimeRequest(
                null,
                LocalDateTime.of(2026, 5, 20, 16, 0),
                LocalDateTime.of(2026, 5, 20, 8, 0),  // endAt < startAt
                DowntimeReason.OTHER, null
        );

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endAt deve ser posterior a startAt");

        verify(plannedDowntimeRepository, never()).save(any());
    }

    @Test
    void shouldThrow_whenDurationExceeds24Hours() {
        // Arrange
        CreatePlannedDowntimeRequest request = new CreatePlannedDowntimeRequest(
                null,
                LocalDateTime.of(2026, 5, 20, 0, 0),
                LocalDateTime.of(2026, 5, 21, 0, 1),  // 1441 minutos
                DowntimeReason.OTHER, null
        );

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1440 minutos");

        verify(plannedDowntimeRepository, never()).save(any());
    }
}
