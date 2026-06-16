package com.industrialhub.backend.common.kpi;

import com.industrialhub.backend.common.kpi.application.OeeCalculator;
import com.industrialhub.backend.common.kpi.application.dto.KpiSummaryResponse;
import com.industrialhub.backend.common.kpi.application.usecase.GetKpiSummaryUseCase;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.domain.TimeRecord;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetKpiSummaryUseCaseTest {

    @Mock private TimeRecordRepository timeRecordRepository;
    @Mock private NonConformanceRepository nonConformanceRepository;
    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private ProductionOrderRepository productionOrderRepository;
    @Mock private ImportProductionBatchRepository importProductionBatchRepository;

    private GetKpiSummaryUseCase useCase;

    @BeforeEach
    void setUp() {
        OeeCalculator oeeCalculator = new OeeCalculator();
        useCase = new GetKpiSummaryUseCase(
                timeRecordRepository, nonConformanceRepository,
                workOrderRepository, equipmentRepository, oeeCalculator,
                productionOrderRepository, importProductionBatchRepository);
    }

    @Test
    void shouldReturnNullOee_whenNoTimeRecords() {
        when(timeRecordRepository.findByPeriodAndOptionalWorker(any(), any(), isNull()))
                .thenReturn(List.of());
        stubCountsAndEquipment();
        when(workOrderRepository.findCompletedCorrectiveForMetrics(null)).thenReturn(List.of());

        KpiSummaryResponse result = useCase.execute();

        assertThat(result.oeeAvgLast30Days()).isNull();
    }

    @Test
    void shouldReturnNullOee_whenRecordsHaveNoShiftData() {
        // Records sem REGISTRO_ENTRADA → availabilityForDay retorna null → OEE null
        TimeRecord processo = TimeRecord.builder()
                .workerId(1L).workerName("W1")
                .profileDate(LocalDate.now())
                .recordType(RecordType.PROCESSO)
                .hours(BigDecimal.valueOf(4.0))
                .build();
        when(timeRecordRepository.findByPeriodAndOptionalWorker(any(), any(), isNull()))
                .thenReturn(List.of(processo));
        stubCountsAndEquipment();
        when(workOrderRepository.findCompletedCorrectiveForMetrics(null)).thenReturn(List.of());

        KpiSummaryResponse result = useCase.execute();

        assertThat(result.oeeAvgLast30Days()).isNull();
    }

    @Test
    void shouldComputeOeeAvg_withValidShiftData() {
        // Worker 1, turno 8h, produtivo 4h → availability = 0.5
        LocalDate date = LocalDate.now().minusDays(1);
        LocalDateTime entryTime = date.atTime(8, 0);
        LocalDateTime exitTime = date.atTime(16, 0);

        TimeRecord entry = TimeRecord.builder()
                .workerId(1L).workerName("W1").profileDate(date)
                .recordType(RecordType.REGISTRO_ENTRADA).startTime(entryTime)
                .build();
        TimeRecord processo = TimeRecord.builder()
                .workerId(1L).workerName("W1").profileDate(date)
                .recordType(RecordType.PROCESSO).hours(BigDecimal.valueOf(4.0))
                .build();
        TimeRecord exit = TimeRecord.builder()
                .workerId(1L).workerName("W1").profileDate(date)
                .recordType(RecordType.REGISTRO_SAIDA).startTime(exitTime)
                .build();

        when(timeRecordRepository.findByPeriodAndOptionalWorker(any(), any(), isNull()))
                .thenReturn(List.of(entry, processo, exit));
        stubCountsAndEquipment();
        when(workOrderRepository.findCompletedCorrectiveForMetrics(null)).thenReturn(List.of());

        KpiSummaryResponse result = useCase.execute();

        assertThat(result.oeeAvgLast30Days()).isCloseTo(0.5, within(0.001));
    }

    @Test
    void shouldReturnNullMttr_whenNoCompletedWorkOrders() {
        when(timeRecordRepository.findByPeriodAndOptionalWorker(any(), any(), isNull()))
                .thenReturn(List.of());
        stubCountsAndEquipment();
        when(workOrderRepository.findCompletedCorrectiveForMetrics(null)).thenReturn(List.of());

        KpiSummaryResponse result = useCase.execute();

        assertThat(result.mttrGlobalHours()).isNull();
    }

    @Test
    void shouldComputeMttr_fromCompletedWorkOrders() {
        // 1h MTTR: startedAt=08:00, closedAt=09:00
        LocalDateTime started = LocalDateTime.of(2025, 1, 1, 8, 0);
        WorkOrder wo = WorkOrder.builder()
                .id(UUID.randomUUID())
                .equipment(Equipment.builder().id(UUID.randomUUID()).build())
                .type(WorkOrderType.CORRECTIVE).priority(WorkOrderPriority.HIGH)
                .title("Falha motor").status(WorkOrderStatus.DONE)
                .openedBy("op1").openedAt(started)
                .startedAt(started).closedAt(started.plusHours(1))
                .build();

        when(timeRecordRepository.findByPeriodAndOptionalWorker(any(), any(), isNull()))
                .thenReturn(List.of());
        stubCountsAndEquipment();
        when(workOrderRepository.findCompletedCorrectiveForMetrics(null)).thenReturn(List.of(wo));

        KpiSummaryResponse result = useCase.execute();

        assertThat(result.mttrGlobalHours()).isCloseTo(1.0, within(0.001));
    }

    @Test
    void shouldAggregateNcCountsAndEquipment_correctly() {
        when(timeRecordRepository.findByPeriodAndOptionalWorker(any(), any(), isNull()))
                .thenReturn(List.of());
        when(nonConformanceRepository.countByStatus(NcStatus.OPEN)).thenReturn(7L);
        when(nonConformanceRepository.countBySeverity(NcSeverity.CRITICAL)).thenReturn(2L);
        when(workOrderRepository.countOpenByEquipmentId(null)).thenReturn(3L);
        when(equipmentRepository.countByActiveTrue()).thenReturn(12L);
        when(workOrderRepository.findCompletedCorrectiveForMetrics(null)).thenReturn(List.of());
        when(productionOrderRepository.countOpenOrders()).thenReturn(0L);
        when(productionOrderRepository.countOverdueOrders(any(LocalDate.class))).thenReturn(0L);
        when(importProductionBatchRepository.findLastSyncAt()).thenReturn(null);

        KpiSummaryResponse result = useCase.execute();

        assertThat(result.totalNcOpen()).isEqualTo(7L);
        assertThat(result.totalNcCritical()).isEqualTo(2L);
        assertThat(result.totalWorkOrdersOpen()).isEqualTo(3L);
        assertThat(result.activeEquipmentCount()).isEqualTo(12L);
    }

    private void stubCountsAndEquipment() {
        when(nonConformanceRepository.countByStatus(NcStatus.OPEN)).thenReturn(0L);
        when(nonConformanceRepository.countBySeverity(NcSeverity.CRITICAL)).thenReturn(0L);
        when(workOrderRepository.countOpenByEquipmentId(null)).thenReturn(0L);
        when(equipmentRepository.countByActiveTrue()).thenReturn(0L);
        when(productionOrderRepository.countOpenOrders()).thenReturn(0L);
        when(productionOrderRepository.countOverdueOrders(any(LocalDate.class))).thenReturn(0L);
        when(importProductionBatchRepository.findLastSyncAt()).thenReturn(null);
    }
}
