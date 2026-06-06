package com.industrialhub.backend.maintenance;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.maintenance.application.dto.CalibrationScheduleResponse;
import com.industrialhub.backend.maintenance.application.dto.CreateCalibrationScheduleRequest;
import com.industrialhub.backend.maintenance.application.usecase.CreateCalibrationScheduleUseCase;
import com.industrialhub.backend.maintenance.domain.CalibrationSchedule;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentDecommissionedException;
import com.industrialhub.backend.maintenance.domain.EquipmentNotFoundException;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.EquipmentType;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationScheduleRepository;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * US-121 ACs cobertos:
 * (c) DECOMMISSIONED ao criar plano â†’ EquipmentDecommissionedException (422)
 * (a) nextDueAt = today + intervalDays quando sem lastCalibratedAt
 * equipment not found â†’ EquipmentNotFoundException (404)
 */
@ExtendWith(MockitoExtension.class)
class CreateCalibrationScheduleUseCaseTest {

    @Mock private CalibrationScheduleRepository scheduleRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private AuditService auditService;

    private CreateCalibrationScheduleUseCase useCase;

    private Equipment operationalEquipment;
    private Equipment decommissionedEquipment;

    @BeforeEach
    void setUp() {
        useCase = new CreateCalibrationScheduleUseCase(
                scheduleRepository, equipmentRepository, auditService);

        operationalEquipment = Equipment.builder()
                .id(UUID.randomUUID())
                .code("EQ-001")
                .name("PaquÃ­metro Mitutoyo")
                .type(EquipmentType.TOOL)
                .status(EquipmentStatus.OPERATIONAL)
                .active(true)
                .build();

        decommissionedEquipment = Equipment.builder()
                .id(UUID.randomUUID())
                .code("EQ-DEAD")
                .name("BalanÃ§a descontinuada")
                .type(EquipmentType.TOOL)
                .status(EquipmentStatus.DECOMMISSIONED)
                .active(false)
                .build();
    }

    // â”€â”€â”€ AC (c) US-121: DECOMMISSIONED â†’ EquipmentDecommissionedException (422) â”€â”€

    @Test
    void shouldThrow_whenEquipmentIsDecommissioned() {
        // AC US-121 (c): equipamento DECOMMISSIONED â†’ exceÃ§Ã£o mapeada para 422
        var request = new CreateCalibrationScheduleRequest(
                decommissionedEquipment.getId(), 90, null);

        when(equipmentRepository.findByIdAndActiveTrue(decommissionedEquipment.getId()))
                .thenReturn(Optional.of(decommissionedEquipment));

        assertThatThrownBy(() -> useCase.execute(request, "supervisor1"))
                .isInstanceOf(EquipmentDecommissionedException.class);
    }

    // â”€â”€â”€ AC US-121: equipment not found â†’ EquipmentNotFoundException (404) â”€â”€

    @Test
    void shouldThrow_whenEquipmentNotFound() {
        UUID unknownId = UUID.randomUUID();
        var request = new CreateCalibrationScheduleRequest(unknownId, 30, null);

        when(equipmentRepository.findByIdAndActiveTrue(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(request, "supervisor1"))
                .isInstanceOf(EquipmentNotFoundException.class);
    }

    // â”€â”€â”€ AC (a) US-121: nextDueAt = today + intervalDays (sem lastCalibratedAt) â”€

    @Test
    void shouldSetNextDueAt_toTodayPlusIntervalDays() {
        // AC US-121 (a): sem histÃ³rico â†’ nextDueAt = hoje + intervalDays
        int intervalDays = 90;
        var request = new CreateCalibrationScheduleRequest(
                operationalEquipment.getId(), intervalDays, null);

        when(equipmentRepository.findByIdAndActiveTrue(operationalEquipment.getId()))
                .thenReturn(Optional.of(operationalEquipment));

        ArgumentCaptor<CalibrationSchedule> captor = ArgumentCaptor.forClass(CalibrationSchedule.class);
        when(scheduleRepository.save(captor.capture())).thenAnswer(inv -> {
            CalibrationSchedule s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        CalibrationScheduleResponse response = useCase.execute(request, "supervisor1");

        CalibrationSchedule saved = captor.getValue();
        assertThat(saved.getNextDueAt()).isEqualTo(LocalDate.now().plusDays(intervalDays));
        assertThat(saved.getLastCalibratedAt()).isNull();
        assertThat(response.active()).isTrue();
        assertThat(response.equipmentId()).isEqualTo(operationalEquipment.getId());
        assertThat(response.overdue()).isFalse();
    }

    @Test
    void shouldPersistExternalProvider() {
        var request = new CreateCalibrationScheduleRequest(
                operationalEquipment.getId(), 180, "Metrologia INMETRO Ltda");

        when(equipmentRepository.findByIdAndActiveTrue(operationalEquipment.getId()))
                .thenReturn(Optional.of(operationalEquipment));

        ArgumentCaptor<CalibrationSchedule> captor = ArgumentCaptor.forClass(CalibrationSchedule.class);
        when(scheduleRepository.save(captor.capture())).thenAnswer(inv -> {
            CalibrationSchedule s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        useCase.execute(request, "supervisor1");

        assertThat(captor.getValue().getExternalProvider()).isEqualTo("Metrologia INMETRO Ltda");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("supervisor1");
    }

    @Test
    void shouldMarkScheduleAsActive_onCreation() {
        var request = new CreateCalibrationScheduleRequest(
                operationalEquipment.getId(), 30, null);

        when(equipmentRepository.findByIdAndActiveTrue(operationalEquipment.getId()))
                .thenReturn(Optional.of(operationalEquipment));
        when(scheduleRepository.save(any())).thenAnswer(inv -> {
            CalibrationSchedule s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        CalibrationScheduleResponse response = useCase.execute(request, "supervisor1");

        assertThat(response.active()).isTrue();
    }
}
