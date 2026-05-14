package com.industrialhub.backend.maintenance;

import com.industrialhub.backend.maintenance.application.dto.WorkOrderMetricsResponse;
import com.industrialhub.backend.maintenance.application.usecase.GetWorkOrderMetricsUseCase;
import com.industrialhub.backend.maintenance.domain.Equipment;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetWorkOrderMetricsUseCaseTest {

    @Mock
    private WorkOrderRepository repository;

    private GetWorkOrderMetricsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetWorkOrderMetricsUseCase(repository);
    }

    private WorkOrder buildCompletedCorrective(long durationSeconds) {
        LocalDateTime started = LocalDateTime.of(2025, 1, 1, 8, 0);
        LocalDateTime closed = started.plusSeconds(durationSeconds);
        return WorkOrder.builder()
                .id(UUID.randomUUID())
                .equipment(Equipment.builder().id(UUID.randomUUID()).build())
                .type(WorkOrderType.CORRECTIVE)
                .title("OS Teste")
                .priority(WorkOrderPriority.MEDIUM)
                .status(WorkOrderStatus.DONE)
                .openedBy("operator1")
                .openedAt(LocalDateTime.now())
                .startedAt(started)
                .closedAt(closed)
                .build();
    }

    @Test
    void shouldReturnNullMttr_whenNoCompletedCorrectiveOrders() {
        when(repository.findCompletedCorrectiveForMetrics(null)).thenReturn(List.of());
        when(repository.countByEquipmentId(null)).thenReturn(5L);
        when(repository.countOpenByEquipmentId(null)).thenReturn(2L);

        WorkOrderMetricsResponse result = useCase.execute(null);

        assertThat(result.mttr()).isNull();
        assertThat(result.totalOrders()).isEqualTo(5L);
        assertThat(result.openOrders()).isEqualTo(2L);
    }

    @Test
    void shouldCalculateMttr_fromSingleCompletedOrder() {
        // 3600 seconds = 1 hour
        WorkOrder wo = buildCompletedCorrective(3600);
        when(repository.findCompletedCorrectiveForMetrics(null)).thenReturn(List.of(wo));
        when(repository.countByEquipmentId(null)).thenReturn(1L);
        when(repository.countOpenByEquipmentId(null)).thenReturn(0L);

        WorkOrderMetricsResponse result = useCase.execute(null);

        assertThat(result.mttr()).isCloseTo(1.0, within(0.001));
    }

    @Test
    void shouldCalculateMttr_asAverageOfMultipleOrders() {
        // 3600s = 1h, 7200s = 2h → average = 1.5h
        WorkOrder wo1 = buildCompletedCorrective(3600);
        WorkOrder wo2 = buildCompletedCorrective(7200);
        when(repository.findCompletedCorrectiveForMetrics(null)).thenReturn(List.of(wo1, wo2));
        when(repository.countByEquipmentId(null)).thenReturn(3L);
        when(repository.countOpenByEquipmentId(null)).thenReturn(1L);

        WorkOrderMetricsResponse result = useCase.execute(null);

        assertThat(result.mttr()).isCloseTo(1.5, within(0.001));
        assertThat(result.totalOrders()).isEqualTo(3L);
        assertThat(result.openOrders()).isEqualTo(1L);
    }

    @Test
    void shouldPassEquipmentIdToRepository() {
        UUID equipmentId = UUID.randomUUID();
        when(repository.findCompletedCorrectiveForMetrics(equipmentId)).thenReturn(List.of());
        when(repository.countByEquipmentId(equipmentId)).thenReturn(0L);
        when(repository.countOpenByEquipmentId(equipmentId)).thenReturn(0L);

        WorkOrderMetricsResponse result = useCase.execute(equipmentId);

        assertThat(result.mttr()).isNull();
        assertThat(result.totalOrders()).isEqualTo(0L);
    }

    @Test
    void shouldReturnZeroCounts_whenNoOrders() {
        when(repository.findCompletedCorrectiveForMetrics(any())).thenReturn(List.of());
        when(repository.countByEquipmentId(any())).thenReturn(0L);
        when(repository.countOpenByEquipmentId(any())).thenReturn(0L);

        WorkOrderMetricsResponse result = useCase.execute(null);

        assertThat(result.mttr()).isNull();
        assertThat(result.totalOrders()).isZero();
        assertThat(result.openOrders()).isZero();
    }
}
