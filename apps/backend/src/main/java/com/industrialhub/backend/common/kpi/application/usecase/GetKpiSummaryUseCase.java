package com.industrialhub.backend.common.kpi.application.usecase;

import com.industrialhub.backend.common.kpi.application.OeeCalculator;
import com.industrialhub.backend.common.kpi.application.dto.KpiSummaryResponse;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.OptionalDouble;

@Service
public class GetKpiSummaryUseCase {

    private final TimeRecordRepository timeRecordRepository;
    private final NonConformanceRepository nonConformanceRepository;
    private final WorkOrderRepository workOrderRepository;
    private final EquipmentRepository equipmentRepository;
    private final OeeCalculator oeeCalculator;

    public GetKpiSummaryUseCase(TimeRecordRepository timeRecordRepository,
                                 NonConformanceRepository nonConformanceRepository,
                                 WorkOrderRepository workOrderRepository,
                                 EquipmentRepository equipmentRepository,
                                 OeeCalculator oeeCalculator) {
        this.timeRecordRepository = timeRecordRepository;
        this.nonConformanceRepository = nonConformanceRepository;
        this.workOrderRepository = workOrderRepository;
        this.equipmentRepository = equipmentRepository;
        this.oeeCalculator = oeeCalculator;
    }

    @Transactional(readOnly = true)
    public KpiSummaryResponse execute() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(29);

        Double oeeAvg = oeeCalculator.computeAvg(
                timeRecordRepository.findByPeriodAndOptionalWorker(start, end, null));
        long totalNcOpen = nonConformanceRepository.countByStatus(NcStatus.OPEN);
        long totalNcCritical = nonConformanceRepository.countBySeverity(NcSeverity.CRITICAL);
        long totalWorkOrdersOpen = workOrderRepository.countOpenByEquipmentId(null);
        Double mttr = computeMttrGlobal();
        long activeEquipment = equipmentRepository.countByActiveTrue();

        return new KpiSummaryResponse(oeeAvg, totalNcOpen, totalNcCritical,
                totalWorkOrdersOpen, mttr, activeEquipment);
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
