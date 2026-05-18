package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.MttrTrendResponse;
import com.industrialhub.backend.maintenance.application.dto.WoSummaryResponse;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GetMaintenanceAnalyticsUseCase {

    private static final int MIN_MONTHS = 3;
    private static final int MAX_MONTHS = 24;

    private final WorkOrderRepository workOrderRepository;

    public GetMaintenanceAnalyticsUseCase(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    public MttrTrendResponse executeMttrTrend(int months) {
        if (months < MIN_MONTHS || months > MAX_MONTHS) {
            throw new IllegalArgumentException("months deve ser entre " + MIN_MONTHS + " e " + MAX_MONTHS);
        }

        // Buscar todas as OSs CORRECTIVE + DONE com startedAt não nulo
        List<WorkOrder> completedOrders = workOrderRepository.findAllCompletedCorrectiveForMttr();

        // Agrupar por mês de closedAt
        Map<YearMonth, List<WorkOrder>> byMonth = completedOrders.stream()
                .filter(wo -> wo.getClosedAt() != null)
                .collect(Collectors.groupingBy(wo -> YearMonth.from(wo.getClosedAt())));

        // Construir lista de meses (dos últimos N meses) em ordem crescente
        YearMonth currentMonth = YearMonth.now();
        List<String> monthLabels = new ArrayList<>();
        List<Double> mttrValues = new ArrayList<>();

        for (int i = months - 1; i >= 0; i--) {
            YearMonth month = currentMonth.minusMonths(i);
            monthLabels.add(month.toString()); // "YYYY-MM"

            List<WorkOrder> monthOrders = byMonth.getOrDefault(month, List.of());

            if (monthOrders.isEmpty()) {
                mttrValues.add(null);
            } else {
                double avgMttrHours = monthOrders.stream()
                        .filter(wo -> wo.getStartedAt() != null && wo.getClosedAt() != null)
                        .mapToDouble(wo -> Duration.between(wo.getStartedAt(), wo.getClosedAt()).toSeconds() / 3600.0)
                        .average()
                        .orElse(0.0);
                mttrValues.add(avgMttrHours == 0.0 ? null : avgMttrHours);
            }
        }

        return new MttrTrendResponse(monthLabels, mttrValues);
    }

    public WoSummaryResponse executeWoSummary() {
        // Contar por status via agregação JPQL — todos os status com 0 se não houver
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (WorkOrderStatus status : WorkOrderStatus.values()) {
            byStatus.put(status.name(), 0L);
        }
        workOrderRepository.countByStatus()
                .forEach(row -> byStatus.put(((WorkOrderStatus) row[0]).name(), (Long) row[1]));

        // Contar por tipo via agregação JPQL — todos os tipos com 0 se não houver
        Map<String, Long> byType = new LinkedHashMap<>();
        for (WorkOrderType type : WorkOrderType.values()) {
            byType.put(type.name(), 0L);
        }
        workOrderRepository.countByType()
                .forEach(row -> byType.put(((WorkOrderType) row[0]).name(), (Long) row[1]));

        return new WoSummaryResponse(byStatus, byType);
    }
}
