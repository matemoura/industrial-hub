package com.industrialhub.backend.maintenance;

import com.industrialhub.backend.maintenance.application.usecase.RunDueSchedulesUseCase;
import com.industrialhub.backend.maintenance.application.usecase.SchedulePlanProcessorService;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentType;
import com.industrialhub.backend.maintenance.domain.MaintenanceSchedule;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunDueSchedulesUseCaseTest {

    @Mock private MaintenanceScheduleRepository scheduleRepository;
    @Mock private SchedulePlanProcessorService planProcessorService;

    private RunDueSchedulesUseCase useCase;

    private Equipment equipment;

    @BeforeEach
    void setUp() {
        useCase = new RunDueSchedulesUseCase(scheduleRepository, planProcessorService);
        equipment = Equipment.builder()
                .id(UUID.randomUUID())
                .code("EQ-001")
                .name("Torno CNC")
                .type(EquipmentType.MACHINE)
                .active(true)
                .build();
    }

    private MaintenanceSchedule buildSchedule(ScheduleRecurrence recurrence, Integer dow, Integer dom) {
        return MaintenanceSchedule.builder()
                .id(UUID.randomUUID())
                .equipment(equipment)
                .title("Plano teste")
                .priority(WorkOrderPriority.MEDIUM)
                .recurrence(recurrence)
                .dayOfWeek(dow)
                .dayOfMonth(dom)
                .nextRunAt(LocalDate.now().minusDays(1))
                .active(true)
                .createdBy("supervisor1")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void shouldReturnZero_whenNoDueSchedules() {
        when(scheduleRepository.findDueSchedules(any())).thenReturn(List.of());

        int result = useCase.execute();

        assertThat(result).isZero();
        verify(planProcessorService, never()).processOne(any(), any());
    }

    @Test
    void shouldDelegateToProcessorService_forDueSchedule() {
        MaintenanceSchedule schedule = buildSchedule(ScheduleRecurrence.DAILY, null, null);
        when(scheduleRepository.findDueSchedules(any())).thenReturn(List.of(schedule));

        int result = useCase.execute();

        assertThat(result).isEqualTo(1);
        verify(planProcessorService).processOne(eq(schedule), any(LocalDate.class));
    }

    @Test
    void shouldPassTodayToProcessor() {
        MaintenanceSchedule schedule = buildSchedule(ScheduleRecurrence.DAILY, null, null);
        when(scheduleRepository.findDueSchedules(any())).thenReturn(List.of(schedule));

        useCase.execute();

        verify(planProcessorService).processOne(eq(schedule), eq(LocalDate.now()));
    }

    @Test
    void shouldProcessAllSchedules_evenIfOneFails() {
        MaintenanceSchedule s1 = buildSchedule(ScheduleRecurrence.DAILY, null, null);
        MaintenanceSchedule s2 = buildSchedule(ScheduleRecurrence.WEEKLY, 3, null);
        when(scheduleRepository.findDueSchedules(any())).thenReturn(List.of(s1, s2));
        // s1 throws, s2 succeeds
        doThrow(new RuntimeException("DB error"))
                .doNothing()
                .when(planProcessorService).processOne(any(), any());

        int result = useCase.execute();

        assertThat(result).isEqualTo(1); // only s2 succeeded
        verify(planProcessorService, times(2)).processOne(any(), any());
    }

    @Test
    void shouldCreateMultipleWorkOrders_forMultipleDueSchedules() {
        MaintenanceSchedule s1 = buildSchedule(ScheduleRecurrence.DAILY, null, null);
        MaintenanceSchedule s2 = buildSchedule(ScheduleRecurrence.MONTHLY, null, 10);
        when(scheduleRepository.findDueSchedules(any())).thenReturn(List.of(s1, s2));

        int result = useCase.execute();

        assertThat(result).isEqualTo(2);
        verify(planProcessorService, times(2)).processOne(any(), any());
    }
}
