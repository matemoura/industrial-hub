package com.industrialhub.backend.maintenance;

import com.industrialhub.backend.maintenance.application.dto.MttrTrendResponse;
import com.industrialhub.backend.maintenance.application.dto.WoSummaryResponse;
import com.industrialhub.backend.maintenance.application.usecase.GetMaintenanceAnalyticsUseCase;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.EquipmentType;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMaintenanceAnalyticsUseCaseTest {

    @Mock private WorkOrderRepository workOrderRepository;

    private GetMaintenanceAnalyticsUseCase useCase;
    private Equipment equipment;

    @BeforeEach
    void setUp() {
        useCase = new GetMaintenanceAnalyticsUseCase(workOrderRepository);
        equipment = Equipment.builder()
                .id(UUID.randomUUID())
                .code("EQ-001")
                .name("Torno CNC")
                .type(EquipmentType.MACHINE)
                .status(EquipmentStatus.OPERATIONAL)
                .active(true)
                .build();
    }

    // --- MTTR Trend tests ---

    @Test
    void mttr_shouldThrow_forInvalidMonths() {
        assertThatThrownBy(() -> useCase.executeMttrTrend(2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("months deve ser entre 3 e 24");
    }

    @Test
    void mttr_shouldThrow_forMonthsExceedingMax() {
        assertThatThrownBy(() -> useCase.executeMttrTrend(25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("months deve ser entre 3 e 24");
    }

    @Test
    void mttr_shouldReturnNullValues_forMonthsWithoutCompletedOrders() {
        when(workOrderRepository.findAllCompletedCorrectiveForMttr()).thenReturn(List.of());

        MttrTrendResponse response = useCase.executeMttrTrend(6);

        assertThat(response.monthLabels()).hasSize(6);
        assertThat(response.mttrValues()).hasSize(6);
        assertThat(response.mttrValues()).containsOnly((Double) null);
    }

    @Test
    void mttr_shouldReturnCorrectMttrForMonthWithOrders() {
        // Work order completed this month, took 4 hours
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startedAt = now.withDayOfMonth(5).withHour(8).withMinute(0);
        LocalDateTime closedAt = startedAt.plusHours(4);

        WorkOrder wo = buildWorkOrder(WorkOrderType.CORRECTIVE, WorkOrderStatus.DONE, startedAt, closedAt);

        when(workOrderRepository.findAllCompletedCorrectiveForMttr()).thenReturn(List.of(wo));

        MttrTrendResponse response = useCase.executeMttrTrend(3);

        // The last month label should be current month
        String lastLabel = response.monthLabels().get(response.monthLabels().size() - 1);
        assertThat(lastLabel).matches("\\d{4}-\\d{2}");

        // At least one value should be non-null (current month has an order)
        assertThat(response.mttrValues()).anyMatch(v -> v != null && v > 0);
    }

    @Test
    void mttr_shouldReturnNullForMonthWithNoOrders_andValueForMonthWithOrders() {
        // Order from current month
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startedAt = now.withDayOfMonth(1).withHour(8).withMinute(0);
        LocalDateTime closedAt = startedAt.plusHours(2);

        WorkOrder wo = buildWorkOrder(WorkOrderType.CORRECTIVE, WorkOrderStatus.DONE, startedAt, closedAt);
        when(workOrderRepository.findAllCompletedCorrectiveForMttr()).thenReturn(List.of(wo));

        MttrTrendResponse response = useCase.executeMttrTrend(6);

        // Some months should be null (no orders), at least one should have a value
        assertThat(response.mttrValues()).anyMatch(v -> v == null);
        assertThat(response.mttrValues()).anyMatch(v -> v != null);
    }

    // --- WO Summary tests ---

    @Test
    void woSummary_shouldReturnAllZeros_whenNoOrders() {
        when(workOrderRepository.countByStatus()).thenReturn(List.of());
        when(workOrderRepository.countByType()).thenReturn(List.of());

        WoSummaryResponse response = useCase.executeWoSummary();

        assertThat(response.byStatus()).hasSize(WorkOrderStatus.values().length);
        assertThat(response.byType()).hasSize(WorkOrderType.values().length);
        assertThat(response.byStatus().values()).containsOnly(0L);
        assertThat(response.byType().values()).containsOnly(0L);
    }

    @Test
    void woSummary_shouldReturnCorrectDistribution() {
        List<Object[]> statusRows = new ArrayList<>();
        statusRows.add(new Object[]{WorkOrderStatus.OPEN, 1L});
        statusRows.add(new Object[]{WorkOrderStatus.IN_PROGRESS, 1L});
        statusRows.add(new Object[]{WorkOrderStatus.DONE, 1L});

        List<Object[]> typeRows = new ArrayList<>();
        typeRows.add(new Object[]{WorkOrderType.CORRECTIVE, 2L});
        typeRows.add(new Object[]{WorkOrderType.PREVENTIVE, 1L});

        when(workOrderRepository.countByStatus()).thenReturn(statusRows);
        when(workOrderRepository.countByType()).thenReturn(typeRows);

        WoSummaryResponse response = useCase.executeWoSummary();

        assertThat(response.byStatus().get("OPEN")).isEqualTo(1L);
        assertThat(response.byStatus().get("IN_PROGRESS")).isEqualTo(1L);
        assertThat(response.byStatus().get("DONE")).isEqualTo(1L);
        assertThat(response.byStatus().get("CANCELLED")).isEqualTo(0L);
        assertThat(response.byType().get("CORRECTIVE")).isEqualTo(2L);
        assertThat(response.byType().get("PREVENTIVE")).isEqualTo(1L);
    }

    @Test
    void woSummary_withOnlyOneStatus_restShouldBeZero() {
        List<Object[]> statusRows = new ArrayList<>();
        statusRows.add(new Object[]{WorkOrderStatus.DONE, 1L});
        List<Object[]> typeRows = new ArrayList<>();
        typeRows.add(new Object[]{WorkOrderType.CORRECTIVE, 1L});

        when(workOrderRepository.countByStatus()).thenReturn(statusRows);
        when(workOrderRepository.countByType()).thenReturn(typeRows);

        WoSummaryResponse response = useCase.executeWoSummary();

        assertThat(response.byStatus().get("DONE")).isEqualTo(1L);
        assertThat(response.byStatus().get("OPEN")).isEqualTo(0L);
        assertThat(response.byStatus().get("IN_PROGRESS")).isEqualTo(0L);
        assertThat(response.byStatus().get("CANCELLED")).isEqualTo(0L);
    }

    // --- WO Summary por shiftId tests (US-056) ---

    @Test
    void woSummary_withShiftId_shouldReturnAllZeros_whenNoOrders() {
        UUID shiftId = UUID.randomUUID();
        when(workOrderRepository.countByStatusAndShift(shiftId)).thenReturn(List.of());
        when(workOrderRepository.countByTypeAndShift(shiftId)).thenReturn(List.of());

        WoSummaryResponse response = useCase.executeWoSummary(shiftId);

        assertThat(response.byStatus().values()).containsOnly(0L);
        assertThat(response.byType().values()).containsOnly(0L);
    }

    @Test
    void woSummary_withNullShiftId_shouldCallNonShiftQuery() {
        when(workOrderRepository.countByStatus()).thenReturn(List.of());
        when(workOrderRepository.countByType()).thenReturn(List.of());

        useCase.executeWoSummary(null);

        verify(workOrderRepository).countByStatus();
        verify(workOrderRepository).countByType();
        verify(workOrderRepository, never()).countByStatusAndShift(any());
        verify(workOrderRepository, never()).countByTypeAndShift(any());
    }

    private WorkOrder buildWorkOrder(WorkOrderType type, WorkOrderStatus status,
                                     LocalDateTime startedAt, LocalDateTime closedAt) {
        return WorkOrder.builder()
                .id(UUID.randomUUID())
                .equipment(equipment)
                .type(type)
                .title("Test WO")
                .priority(WorkOrderPriority.MEDIUM)
                .status(status)
                .openedBy("supervisor")
                .openedAt(LocalDateTime.now().minusDays(10))
                .startedAt(startedAt)
                .closedAt(closedAt)
                .build();
    }
}
