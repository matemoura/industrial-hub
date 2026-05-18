package com.industrialhub.backend.maintenance;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.application.dto.ScheduleResponse;
import com.industrialhub.backend.maintenance.application.dto.UpdateScheduleRequest;
import com.industrialhub.backend.maintenance.application.usecase.UpdateScheduleUseCase;
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
class UpdateScheduleUseCaseTest {

    @Mock private MaintenanceScheduleRepository scheduleRepository;
    @Mock private AuditService auditService;

    private UpdateScheduleUseCase useCase;

    private Equipment equipment;
    private MaintenanceSchedule existingSchedule;

    @BeforeEach
    void setUp() {
        useCase = new UpdateScheduleUseCase(scheduleRepository, auditService);

        equipment = Equipment.builder()
                .id(UUID.randomUUID())
                .code("EQ-001")
                .name("Torno CNC")
                .type(EquipmentType.MACHINE)
                .status(EquipmentStatus.OPERATIONAL)
                .active(true)
                .build();

        existingSchedule = MaintenanceSchedule.builder()
                .id(UUID.randomUUID())
                .equipment(equipment)
                .title("Old Title")
                .recurrence(ScheduleRecurrence.DAILY)
                .priority(WorkOrderPriority.LOW)
                .active(true)
                .createdBy("supervisor1")
                .createdAt(LocalDateTime.now().minusDays(7))
                .nextRunAt(LocalDate.now().plusDays(1))
                .build();
    }

    @Test
    void shouldUpdateScheduleFields_andRecalculateNextRunAt() {
        UUID id = existingSchedule.getId();
        UpdateScheduleRequest request = new UpdateScheduleRequest(
                "New Title", "Nova desc", WorkOrderPriority.HIGH, ScheduleRecurrence.DAILY, null, null);

        when(scheduleRepository.findById(id)).thenReturn(Optional.of(existingSchedule));
        when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScheduleResponse response = useCase.execute(id, request, "supervisor1");

        assertThat(response.title()).isEqualTo("New Title");
        assertThat(response.description()).isEqualTo("Nova desc");
        assertThat(response.priority()).isEqualTo(WorkOrderPriority.HIGH);
        assertThat(response.nextRunAt()).isNotNull();
        assertThat(response.nextRunAt()).isAfterOrEqualTo(LocalDate.now());
    }

    @Test
    void shouldThrow_whenScheduleNotFound() {
        UUID unknownId = UUID.randomUUID();
        UpdateScheduleRequest request = new UpdateScheduleRequest(
                "Title", null, WorkOrderPriority.LOW, ScheduleRecurrence.DAILY, null, null);

        when(scheduleRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(unknownId, request, "supervisor1"))
                .isInstanceOf(ScheduleNotFoundException.class);
    }

    @Test
    void shouldCallAuditService_withScheduleUpdatedAction() {
        UUID id = existingSchedule.getId();
        UpdateScheduleRequest request = new UpdateScheduleRequest(
                "Audit Title", null, WorkOrderPriority.MEDIUM, ScheduleRecurrence.WEEKLY, 3, null);

        when(scheduleRepository.findById(id)).thenReturn(Optional.of(existingSchedule));
        when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(id, request, "adminUser");

        verify(auditService).log(eq("adminUser"), eq(AuditAction.SCHEDULE_UPDATED),
                eq("MaintenanceSchedule"), eq(id), any());
    }
}
