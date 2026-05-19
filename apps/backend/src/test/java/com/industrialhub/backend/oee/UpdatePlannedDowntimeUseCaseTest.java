package com.industrialhub.backend.oee;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.oee.application.dto.PlannedDowntimeResponse;
import com.industrialhub.backend.oee.application.dto.UpdatePlannedDowntimeRequest;
import com.industrialhub.backend.oee.application.usecase.UpdatePlannedDowntimeUseCase;
import com.industrialhub.backend.oee.domain.DowntimeReason;
import com.industrialhub.backend.oee.domain.PlannedDowntime;
import com.industrialhub.backend.oee.domain.PlannedDowntimeNotFoundException;
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
class UpdatePlannedDowntimeUseCaseTest {

    @Mock
    private PlannedDowntimeRepository plannedDowntimeRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private AuditService auditService;

    private UpdatePlannedDowntimeUseCase useCase;

    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 5, 20, 8, 0);
    private static final LocalDateTime END_AT   = LocalDateTime.of(2026, 5, 20, 16, 0);

    @BeforeEach
    void setUp() {
        useCase = new UpdatePlannedDowntimeUseCase(plannedDowntimeRepository, equipmentRepository, auditService);
    }

    // ─── Cenário 1: Atualização bem-sucedida null → null (planta inteira) ───────

    @Test
    void shouldUpdateDowntime_withNullEquipment_plantWide() {
        UUID id = UUID.randomUUID();
        PlannedDowntime existing = PlannedDowntime.builder()
                .id(id)
                .equipment(null)
                .startAt(LocalDateTime.of(2026, 5, 19, 8, 0))
                .endAt(LocalDateTime.of(2026, 5, 19, 10, 0))
                .reason(DowntimeReason.OTHER)
                .registeredBy("supervisor")
                .registeredAt(LocalDateTime.now())
                .build();

        UpdatePlannedDowntimeRequest request = new UpdatePlannedDowntimeRequest(
                null, START_AT, END_AT, DowntimeReason.HOLIDAY, "Feriado nacional"
        );

        PlannedDowntime saved = PlannedDowntime.builder()
                .id(id)
                .equipment(null)
                .startAt(START_AT)
                .endAt(END_AT)
                .reason(DowntimeReason.HOLIDAY)
                .description("Feriado nacional")
                .registeredBy("supervisor")
                .registeredAt(LocalDateTime.now())
                .build();

        when(plannedDowntimeRepository.findById(id)).thenReturn(Optional.of(existing));
        when(plannedDowntimeRepository.save(any(PlannedDowntime.class))).thenReturn(saved);

        PlannedDowntimeResponse response = useCase.execute(id, request, "supervisor");

        assertThat(response.equipmentId()).isNull();
        assertThat(response.startAt()).isEqualTo(START_AT);
        assertThat(response.endAt()).isEqualTo(END_AT);
        assertThat(response.durationMinutes()).isEqualTo(480);
        assertThat(response.reason()).isEqualTo(DowntimeReason.HOLIDAY);
        assertThat(response.description()).isEqualTo("Feriado nacional");

        verify(equipmentRepository, never()).findByIdAndActiveTrue(any());

        verify(auditService).log(eq("supervisor"), eq(AuditAction.DOWNTIME_UPDATED),
                eq("PlannedDowntime"), any(String.class), any());
    }

    // ─── Cenário 2: Atualização bem-sucedida null → equipment ────────────────────

    @Test
    void shouldUpdateDowntime_addingEquipment() {
        UUID id = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        PlannedDowntime existing = PlannedDowntime.builder()
                .id(id)
                .equipment(null)
                .startAt(LocalDateTime.of(2026, 5, 19, 8, 0))
                .endAt(LocalDateTime.of(2026, 5, 19, 10, 0))
                .reason(DowntimeReason.OTHER)
                .registeredBy("supervisor")
                .registeredAt(LocalDateTime.now())
                .build();

        Equipment equipment = Equipment.builder()
                .id(equipmentId)
                .code("EQ-001")
                .name("Torno CNC")
                .build();

        UpdatePlannedDowntimeRequest request = new UpdatePlannedDowntimeRequest(
                equipmentId, START_AT, END_AT, DowntimeReason.PREVENTIVE_MAINTENANCE, null
        );

        PlannedDowntime saved = PlannedDowntime.builder()
                .id(id)
                .equipment(equipment)
                .startAt(START_AT)
                .endAt(END_AT)
                .reason(DowntimeReason.PREVENTIVE_MAINTENANCE)
                .registeredBy("supervisor")
                .registeredAt(LocalDateTime.now())
                .build();

        when(plannedDowntimeRepository.findById(id)).thenReturn(Optional.of(existing));
        when(equipmentRepository.findByIdAndActiveTrue(equipmentId)).thenReturn(Optional.of(equipment));
        when(plannedDowntimeRepository.save(any(PlannedDowntime.class))).thenReturn(saved);

        PlannedDowntimeResponse response = useCase.execute(id, request, "supervisor");

        assertThat(response.equipmentId()).isEqualTo(equipmentId);
        assertThat(response.equipmentCode()).isEqualTo("EQ-001");
        assertThat(response.equipmentName()).isEqualTo("Torno CNC");
        assertThat(response.durationMinutes()).isEqualTo(480);

        ArgumentCaptor<PlannedDowntime> captor = ArgumentCaptor.forClass(PlannedDowntime.class);
        verify(plannedDowntimeRepository).save(captor.capture());
        assertThat(captor.getValue().getEquipment()).isEqualTo(equipment);
    }

    // ─── Cenário 3: PlannedDowntimeNotFoundException quando id não existe ─────────

    @Test
    void shouldThrow_whenDowntimeNotFound() {
        UUID unknownId = UUID.randomUUID();
        UpdatePlannedDowntimeRequest request = new UpdatePlannedDowntimeRequest(
                null, START_AT, END_AT, DowntimeReason.OTHER, null
        );

        when(plannedDowntimeRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(unknownId, request, "supervisor"))
                .isInstanceOf(PlannedDowntimeNotFoundException.class);

        verify(plannedDowntimeRepository, never()).save(any());
    }

    // ─── Cenário 4: IllegalArgumentException quando endAt <= startAt ──────────────

    @Test
    void shouldThrow_whenEndAtBeforeStartAt() {
        UUID id = UUID.randomUUID();
        PlannedDowntime existing = PlannedDowntime.builder()
                .id(id)
                .startAt(START_AT)
                .endAt(END_AT)
                .reason(DowntimeReason.OTHER)
                .registeredBy("supervisor")
                .registeredAt(LocalDateTime.now())
                .build();

        UpdatePlannedDowntimeRequest request = new UpdatePlannedDowntimeRequest(
                null,
                LocalDateTime.of(2026, 5, 20, 16, 0),
                LocalDateTime.of(2026, 5, 20, 8, 0),  // endAt < startAt
                DowntimeReason.OTHER, null
        );

        when(plannedDowntimeRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(id, request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endAt deve ser posterior a startAt");

        verify(plannedDowntimeRepository, never()).save(any());
    }

    // ─── Cenário 5: IllegalArgumentException quando duração > 1440 min ───────────

    @Test
    void shouldThrow_whenDurationExceeds24Hours() {
        UUID id = UUID.randomUUID();
        PlannedDowntime existing = PlannedDowntime.builder()
                .id(id)
                .startAt(START_AT)
                .endAt(END_AT)
                .reason(DowntimeReason.OTHER)
                .registeredBy("supervisor")
                .registeredAt(LocalDateTime.now())
                .build();

        UpdatePlannedDowntimeRequest request = new UpdatePlannedDowntimeRequest(
                null,
                LocalDateTime.of(2026, 5, 20, 0, 0),
                LocalDateTime.of(2026, 5, 21, 0, 1),  // 1441 minutos
                DowntimeReason.OTHER, null
        );

        when(plannedDowntimeRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(id, request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1440 minutos");

        verify(plannedDowntimeRepository, never()).save(any());
    }

    // ─── Cenário 6: EquipmentNotFoundException quando equipmentId inexistente/inativo ──

    @Test
    void shouldThrow_whenEquipmentNotFoundOrInactive() {
        UUID id = UUID.randomUUID();
        UUID unknownEquipmentId = UUID.randomUUID();

        PlannedDowntime existing = PlannedDowntime.builder()
                .id(id)
                .equipment(null)
                .startAt(START_AT)
                .endAt(END_AT)
                .reason(DowntimeReason.OTHER)
                .registeredBy("supervisor")
                .registeredAt(LocalDateTime.now())
                .build();

        UpdatePlannedDowntimeRequest request = new UpdatePlannedDowntimeRequest(
                unknownEquipmentId, START_AT, END_AT, DowntimeReason.PREVENTIVE_MAINTENANCE, null
        );

        when(plannedDowntimeRepository.findById(id)).thenReturn(Optional.of(existing));
        when(equipmentRepository.findByIdAndActiveTrue(unknownEquipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, request, "admin"))
                .isInstanceOf(EquipmentNotFoundException.class);

        verify(plannedDowntimeRepository, never()).save(any());
    }
}
