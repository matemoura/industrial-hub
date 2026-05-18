package com.industrialhub.backend.maintenance;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.application.usecase.DeactivateScheduleUseCase;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.EquipmentType;
import com.industrialhub.backend.maintenance.domain.MaintenanceSchedule;
import com.industrialhub.backend.maintenance.domain.ScheduleNotFoundException;
import com.industrialhub.backend.maintenance.domain.ScheduleRecurrence;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.infrastructure.MaintenanceScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeactivateScheduleUseCaseTest {

    @Mock private MaintenanceScheduleRepository scheduleRepository;
    @Mock private AuditService auditService;

    private DeactivateScheduleUseCase useCase;

    private Equipment equipment;
    private MaintenanceSchedule activeSchedule;

    @BeforeEach
    void setUp() {
        useCase = new DeactivateScheduleUseCase(scheduleRepository, auditService);

        equipment = Equipment.builder()
                .id(UUID.randomUUID())
                .code("EQ-001")
                .name("Torno CNC")
                .type(EquipmentType.MACHINE)
                .status(EquipmentStatus.OPERATIONAL)
                .active(true)
                .build();

        activeSchedule = MaintenanceSchedule.builder()
                .id(UUID.randomUUID())
                .equipment(equipment)
                .title("Revisão Semanal")
                .recurrence(ScheduleRecurrence.WEEKLY)
                .dayOfWeek(2)
                .priority(WorkOrderPriority.MEDIUM)
                .active(true)
                .createdBy("supervisor1")
                .createdAt(LocalDateTime.now().minusDays(10))
                .nextRunAt(LocalDate.now().plusDays(2))
                .build();
    }

    @Test
    void shouldSetActiveToFalse_whenScheduleFound() {
        UUID id = activeSchedule.getId();
        when(scheduleRepository.findById(id)).thenReturn(Optional.of(activeSchedule));
        when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(id, "supervisor1");

        assertThat(activeSchedule.isActive()).isFalse();
        verify(scheduleRepository).save(activeSchedule);
    }

    @Test
    void shouldThrow_whenScheduleNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(scheduleRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(unknownId, "supervisor1"))
                .isInstanceOf(ScheduleNotFoundException.class);
    }
}
