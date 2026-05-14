package com.industrialhub.backend.common.kpi.application.usecase;

import com.industrialhub.backend.common.kpi.application.dto.KpiSummaryResponse;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.domain.TimeRecord;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

@Service
public class GetKpiSummaryUseCase {

    private final TimeRecordRepository timeRecordRepository;
    private final NonConformanceRepository nonConformanceRepository;
    private final WorkOrderRepository workOrderRepository;
    private final EquipmentRepository equipmentRepository;

    public GetKpiSummaryUseCase(TimeRecordRepository timeRecordRepository,
                                 NonConformanceRepository nonConformanceRepository,
                                 WorkOrderRepository workOrderRepository,
                                 EquipmentRepository equipmentRepository) {
        this.timeRecordRepository = timeRecordRepository;
        this.nonConformanceRepository = nonConformanceRepository;
        this.workOrderRepository = workOrderRepository;
        this.equipmentRepository = equipmentRepository;
    }

    @Transactional(readOnly = true)
    public KpiSummaryResponse execute() {
        Double oeeAvg = computeOeeAvgLast30Days();
        long totalNcOpen = nonConformanceRepository.countByStatus(NcStatus.OPEN);
        long totalNcCritical = nonConformanceRepository.countBySeverity(NcSeverity.CRITICAL);
        long totalWorkOrdersOpen = workOrderRepository.countOpenByEquipmentId(null);
        Double mttr = computeMttrGlobal();
        long activeEquipment = equipmentRepository.countByActiveTrue();

        return new KpiSummaryResponse(oeeAvg, totalNcOpen, totalNcCritical,
                totalWorkOrdersOpen, mttr, activeEquipment);
    }

    private Double computeOeeAvgLast30Days() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(29);
        List<TimeRecord> records = timeRecordRepository.findByPeriodAndOptionalWorker(start, end, null);
        if (records.isEmpty()) return null;

        Map<String, List<TimeRecord>> byWorkerDay = records.stream()
                .collect(Collectors.groupingBy(r -> r.getWorkerId() + "|" + r.getProfileDate()));

        List<Double> availabilities = byWorkerDay.values().stream()
                .map(this::availabilityForDay)
                .filter(Objects::nonNull)
                .toList();

        if (availabilities.isEmpty()) return null;
        return availabilities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private Double availabilityForDay(List<TimeRecord> dayRecords) {
        double productive = dayRecords.stream()
                .filter(r -> r.getRecordType() == RecordType.PROCESSO)
                .map(r -> r.getHours() != null ? r.getHours() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .doubleValue();

        LocalDateTime entry = dayRecords.stream()
                .filter(r -> r.getRecordType() == RecordType.REGISTRO_ENTRADA)
                .map(TimeRecord::getStartTime)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);

        LocalDateTime exit = dayRecords.stream()
                .filter(r -> r.getRecordType() == RecordType.REGISTRO_SAIDA)
                .map(r -> r.getEndTime() != null ? r.getEndTime() : r.getStartTime())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        if (entry == null || exit == null || !exit.isAfter(entry)) return null;
        double shiftHours = Duration.between(entry, exit).toMinutes() / 60.0;
        if (shiftHours == 0) return null;
        return productive / shiftHours;
    }

    private Double computeMttrGlobal() {
        List<WorkOrder> completed = workOrderRepository.findCompletedCorrectiveForMetrics(null);
        if (completed.isEmpty()) return null;
        OptionalDouble avg = completed.stream()
                .mapToLong(wo -> ChronoUnit.SECONDS.between(wo.getStartedAt(), wo.getClosedAt()))
                .average();
        return avg.isPresent() ? avg.getAsDouble() / 3600.0 : null;
    }
}
