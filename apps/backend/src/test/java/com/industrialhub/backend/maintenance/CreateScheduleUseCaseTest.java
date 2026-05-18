package com.industrialhub.backend.maintenance;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;

import java.util.Map;
import com.industrialhub.backend.maintenance.application.dto.CreateScheduleRequest;
import com.industrialhub.backend.maintenance.application.dto.ScheduleResponse;
import com.industrialhub.backend.maintenance.application.usecase.CreateScheduleUseCase;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentNotFoundException;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.EquipmentType;
import com.industrialhub.backend.maintenance.domain.InactiveEquipmentScheduleException;
import com.industrialhub.backend.maintenance.domain.InvalidScheduleRecurrenceException;
import com.industrialhub.backend.maintenance.domain.MaintenanceSchedule;
import com.industrialhub.backend.maintenance.domain.ScheduleRecurrence;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.maintenance.infrastructure.MaintenanceScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateScheduleUseCaseTest {

    @Mock private EquipmentRepository equipmentRepository;
    @Mock private MaintenanceScheduleRepository scheduleRepository;
    @Mock private AuditService auditService;

    private CreateScheduleUseCase useCase;

    private Equipment activeEquipment;

    @BeforeEach
    void setUp() {
        useCase = new CreateScheduleUseCase(equipmentRepository, scheduleRepository, auditService);
        activeEquipment = Equipment.builder()
                .id(UUID.randomUUID())
                .code("EQ-001")
                .name("Torno CNC")
                .type(EquipmentType.MACHINE)
                .status(EquipmentStatus.OPERATIONAL)
                .active(true)
                .build();
    }

    @Test
    void shouldCreateDailySchedule() {
        var request = new CreateScheduleRequest(
                activeEquipment.getId(), "Lubrificação diária", null,
                WorkOrderPriority.MEDIUM, ScheduleRecurrence.DAILY, null, null);

        when(equipmentRepository.findById(activeEquipment.getId())).thenReturn(Optional.of(activeEquipment));
        when(scheduleRepository.save(any())).thenAnswer(inv -> {
            MaintenanceSchedule s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        ScheduleResponse response = useCase.execute(request, "supervisor1");

        assertThat(response.recurrence()).isEqualTo(ScheduleRecurrence.DAILY);
        assertThat(response.nextRunAt()).isNotNull();
        assertThat(response.active()).isTrue();
        assertThat(response.createdBy()).isEqualTo("supervisor1");
        verify(auditService).log(any(String.class), any(AuditAction.class), any(String.class), isA(UUID.class), any(Map.class));
    }

    @Test
    void shouldCreateWeeklySchedule_withDayOfWeek() {
        var request = new CreateScheduleRequest(
                activeEquipment.getId(), "Inspeção semanal", null,
                WorkOrderPriority.HIGH, ScheduleRecurrence.WEEKLY, 5, null);

        when(equipmentRepository.findById(activeEquipment.getId())).thenReturn(Optional.of(activeEquipment));
        when(scheduleRepository.save(any())).thenAnswer(inv -> {
            MaintenanceSchedule s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        ScheduleResponse response = useCase.execute(request, "supervisor1");

        assertThat(response.recurrence()).isEqualTo(ScheduleRecurrence.WEEKLY);
        assertThat(response.dayOfWeek()).isEqualTo(5);
        assertThat(response.nextRunAt()).isNotNull();
    }

    @Test
    void shouldCreateMonthlySchedule_withDayOfMonth() {
        var request = new CreateScheduleRequest(
                activeEquipment.getId(), "Revisão mensal", null,
                WorkOrderPriority.URGENT, ScheduleRecurrence.MONTHLY, null, 15);

        when(equipmentRepository.findById(activeEquipment.getId())).thenReturn(Optional.of(activeEquipment));
        when(scheduleRepository.save(any())).thenAnswer(inv -> {
            MaintenanceSchedule s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        ScheduleResponse response = useCase.execute(request, "supervisor1");

        assertThat(response.recurrence()).isEqualTo(ScheduleRecurrence.MONTHLY);
        assertThat(response.dayOfMonth()).isEqualTo(15);
    }

    @Test
    void shouldThrow_whenEquipmentNotFound() {
        UUID unknownId = UUID.randomUUID();
        var request = new CreateScheduleRequest(
                unknownId, "Test", null, WorkOrderPriority.LOW, ScheduleRecurrence.DAILY, null, null);

        when(equipmentRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(request, "supervisor1"))
                .isInstanceOf(EquipmentNotFoundException.class);
    }

    @Test
    void shouldThrow_whenEquipmentIsInactive() {
        Equipment inactive = Equipment.builder()
                .id(UUID.randomUUID()).code("EQ-002").name("Old")
                .type(EquipmentType.MACHINE).status(EquipmentStatus.DECOMMISSIONED).active(false).build();
        var request = new CreateScheduleRequest(
                inactive.getId(), "Test", null, WorkOrderPriority.LOW, ScheduleRecurrence.DAILY, null, null);

        when(equipmentRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> useCase.execute(request, "supervisor1"))
                .isInstanceOf(InactiveEquipmentScheduleException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    void shouldThrow_forWeeklyWithoutDayOfWeek() {
        var request = new CreateScheduleRequest(
                activeEquipment.getId(), "Test", null,
                WorkOrderPriority.LOW, ScheduleRecurrence.WEEKLY, null, null);

        // validation fires before equipment lookup
        assertThatThrownBy(() -> useCase.execute(request, "supervisor1"))
                .isInstanceOf(InvalidScheduleRecurrenceException.class)
                .hasMessageContaining("dayOfWeek");
    }

    @Test
    void shouldThrow_forMonthlyWithDayOfMonth29() {
        var request = new CreateScheduleRequest(
                activeEquipment.getId(), "Test", null,
                WorkOrderPriority.LOW, ScheduleRecurrence.MONTHLY, null, 29);

        // validation fires before equipment lookup
        assertThatThrownBy(() -> useCase.execute(request, "supervisor1"))
                .isInstanceOf(InvalidScheduleRecurrenceException.class)
                .hasMessageContaining("dayOfMonth");
    }

    @Test
    void shouldPersistWithEquipment() {
        var request = new CreateScheduleRequest(
                activeEquipment.getId(), "Lubrificação", "Lubrificar trilhos",
                WorkOrderPriority.LOW, ScheduleRecurrence.DAILY, null, null);

        when(equipmentRepository.findById(activeEquipment.getId())).thenReturn(Optional.of(activeEquipment));
        ArgumentCaptor<MaintenanceSchedule> captor = ArgumentCaptor.forClass(MaintenanceSchedule.class);
        when(scheduleRepository.save(captor.capture())).thenAnswer(inv -> {
            MaintenanceSchedule s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        useCase.execute(request, "supervisor1");

        MaintenanceSchedule saved = captor.getValue();
        assertThat(saved.getEquipment()).isEqualTo(activeEquipment);
        assertThat(saved.getTitle()).isEqualTo("Lubrificação");
        assertThat(saved.getDescription()).isEqualTo("Lubrificar trilhos");
        assertThat(saved.isActive()).isTrue();
    }
}
