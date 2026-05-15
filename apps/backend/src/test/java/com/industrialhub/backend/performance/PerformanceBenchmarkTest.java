package com.industrialhub.backend.performance;

import com.industrialhub.backend.common.kpi.application.usecase.GetKpiSummaryUseCase;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.EquipmentType;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import com.industrialhub.backend.oee.application.usecase.GetOeeDashboardUseCase;
import com.industrialhub.backend.oee.domain.ImportBatch;
import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.domain.TimeRecord;
import com.industrialhub.backend.oee.infrastructure.ImportBatchRepository;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StopWatch;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PerformanceBenchmarkTest {

    private static final long KPI_LIMIT_MS = 300;
    private static final long OEE_LIMIT_MS = 400;

    // ─── use cases under test ────────────────────────────────────────────────
    @Autowired private GetKpiSummaryUseCase    kpiSummaryUseCase;
    @Autowired private GetOeeDashboardUseCase  oeeDashboardUseCase;

    // ─── repositories for seed ───────────────────────────────────────────────
    @Autowired private ImportBatchRepository       batchRepository;
    @Autowired private TimeRecordRepository        timeRecordRepository;
    @Autowired private NonConformanceRepository    ncRepository;
    @Autowired private EquipmentRepository         equipmentRepository;
    @Autowired private WorkOrderRepository         workOrderRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Seed: 1 000 time_records + 200 NCs + 100 work orders (10 equipment × 10 WOs)
    // ─────────────────────────────────────────────────────────────────────────
    @BeforeAll
    void seedPerformanceData() {
        seedTimeRecords();
        seedNonConformances();
        seedWorkOrders();
    }

    private void seedTimeRecords() {
        // 10 batches × 13 workers × ~8 records each ≈ 1 040 records (trimmed to exactly 1 000)
        List<ImportBatch> batches = new ArrayList<>();
        for (int day = 0; day < 10; day++) {
            LocalDate periodDate = LocalDate.of(2026, 1, 1).plusDays(day);
            ImportBatch batch = ImportBatch.builder()
                    .fileName("perf_seed_" + periodDate + ".xlsx")
                    .importedAt(Instant.now())
                    .periodDate(periodDate)
                    .totalRecords(100)
                    .workerCount(13)
                    .build();
            batches.add(batchRepository.save(batch));
        }

        List<TimeRecord> records = new ArrayList<>(1000);
        int count = 0;
        outer:
        for (ImportBatch batch : batches) {
            LocalDate date = batch.getPeriodDate();
            for (int worker = 1; worker <= 13 && count < 1000; worker++) {
                // clock-in
                records.add(TimeRecord.builder()
                        .batch(batch)
                        .workerId((long) worker)
                        .workerName("Worker " + worker)
                        .profileDate(date)
                        .startTime(date.atTime(8, 0))
                        .endTime(date.atTime(8, 1))
                        .recordType(RecordType.REGISTRO_ENTRADA)
                        .reference("Sistema")
                        .hours(BigDecimal.ZERO)
                        .build());
                count++;
                if (count >= 1000) break outer;

                // productive work
                records.add(TimeRecord.builder()
                        .batch(batch)
                        .workerId((long) worker)
                        .workerName("Worker " + worker)
                        .profileDate(date)
                        .startTime(date.atTime(8, 1))
                        .endTime(date.atTime(12, 0))
                        .recordType(RecordType.PROCESSO)
                        .reference("OP260" + String.format("%05d", worker))
                        .operationNumber(10)
                        .workIdentifier("OPTR" + worker)
                        .description("Montagem Componente " + worker)
                        .hours(new BigDecimal("3.98"))
                        .build());
                count++;
                if (count >= 1000) break outer;

                // indirect activity
                records.add(TimeRecord.builder()
                        .batch(batch)
                        .workerId((long) worker)
                        .workerName("Worker " + worker)
                        .profileDate(date)
                        .startTime(date.atTime(12, 0))
                        .endTime(date.atTime(13, 0))
                        .recordType(RecordType.ATIVIDADE_INDIRETA)
                        .reference("INTERVALO")
                        .description("Almoço")
                        .hours(new BigDecimal("1.00"))
                        .build());
                count++;
                if (count >= 1000) break outer;

                // afternoon productive
                records.add(TimeRecord.builder()
                        .batch(batch)
                        .workerId((long) worker)
                        .workerName("Worker " + worker)
                        .profileDate(date)
                        .startTime(date.atTime(13, 0))
                        .endTime(date.atTime(17, 0))
                        .recordType(RecordType.PROCESSO)
                        .reference("OP260" + String.format("%05d", worker + 100))
                        .operationNumber(20)
                        .workIdentifier("OPTR" + worker + "B")
                        .description("Montagem Fibra " + worker)
                        .hours(new BigDecimal("4.00"))
                        .build());
                count++;
                if (count >= 1000) break outer;
            }
        }
        timeRecordRepository.saveAll(records);
    }

    private void seedNonConformances() {
        NcType[]     types      = NcType.values();
        NcSeverity[] severities = NcSeverity.values();
        NcStatus[]   statuses   = { NcStatus.OPEN, NcStatus.IN_ANALYSIS, NcStatus.CLOSED };

        List<NonConformance> ncs = new ArrayList<>(200);
        for (int i = 0; i < 200; i++) {
            NcStatus status = statuses[i % statuses.length];
            LocalDateTime reportedAt = LocalDateTime.of(2026, 1, 1, 0, 0).plusDays(i % 90);
            ncs.add(NonConformance.builder()
                    .title("NC de performance #" + i)
                    .type(types[i % types.length])
                    .severity(severities[i % severities.length])
                    .status(status)
                    .reportedBy("system")
                    .reportedAt(reportedAt)
                    .closedAt(status == NcStatus.CLOSED ? reportedAt.plusDays(2) : null)
                    .closedBy(status == NcStatus.CLOSED ? "system" : null)
                    .build());
        }
        ncRepository.saveAll(ncs);
    }

    private void seedWorkOrders() {
        EquipmentType[]   eqTypes   = EquipmentType.values();
        WorkOrderPriority[] priorities = WorkOrderPriority.values();
        WorkOrderStatus[]   woStatuses = {
                WorkOrderStatus.OPEN, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.DONE
        };

        List<WorkOrder> workOrders = new ArrayList<>(100);
        for (int e = 0; e < 10; e++) {
            Equipment equipment = equipmentRepository.save(Equipment.builder()
                    .code("EQ-PERF-" + String.format("%03d", e))
                    .name("Equipamento Performance " + e)
                    .type(eqTypes[e % eqTypes.length])
                    .status(EquipmentStatus.OPERATIONAL)
                    .active(true)
                    .build());

            for (int w = 0; w < 10; w++) {
                WorkOrderStatus status = woStatuses[w % woStatuses.length];
                LocalDateTime openedAt = LocalDateTime.of(2026, 1, 1, 0, 0).plusDays(w);
                LocalDateTime startedAt = (status == WorkOrderStatus.IN_PROGRESS
                        || status == WorkOrderStatus.DONE) ? openedAt.plusHours(1) : null;
                LocalDateTime closedAt  = status == WorkOrderStatus.DONE
                        ? openedAt.plusHours(3) : null;

                workOrders.add(WorkOrder.builder()
                        .equipment(equipment)
                        .type(WorkOrderType.CORRECTIVE)
                        .title("OS Performance " + e + "-" + w)
                        .priority(priorities[w % priorities.length])
                        .status(status)
                        .openedBy("system")
                        .openedAt(openedAt)
                        .startedAt(startedAt)
                        .closedAt(closedAt)
                        .build());
            }
        }
        workOrderRepository.saveAll(workOrders);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Benchmarks
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void kpiSummary_respondsWithin300ms() {
        // warm up — JVM / query plan
        kpiSummaryUseCase.execute();

        StopWatch sw = new StopWatch();
        sw.start();
        kpiSummaryUseCase.execute();
        sw.stop();

        assertThat(sw.getTotalTimeMillis())
                .as("GET /kpi/summary should respond within %dms but took %dms",
                        KPI_LIMIT_MS, sw.getTotalTimeMillis())
                .isLessThanOrEqualTo(KPI_LIMIT_MS);
    }

    @Test
    void oeeDashboard_respondsWithin400ms() {
        LocalDate end   = LocalDate.of(2026, 1, 10);
        LocalDate start = end.minusDays(30);

        // warm up
        oeeDashboardUseCase.execute(start, end, null);

        StopWatch sw = new StopWatch();
        sw.start();
        oeeDashboardUseCase.execute(start, end, null);
        sw.stop();

        assertThat(sw.getTotalTimeMillis())
                .as("GET /oee/dashboard should respond within %dms but took %dms",
                        OEE_LIMIT_MS, sw.getTotalTimeMillis())
                .isLessThanOrEqualTo(OEE_LIMIT_MS);
    }
}
