package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.common.application.dto.TimeSeriesResponse;
import com.industrialhub.backend.qms.application.dto.NcParetoResponse;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GetNcAnalyticsUseCase {

    private static final List<Integer> ALLOWED_DAYS = List.of(30, 90, 180);
    private static final int MIN_WEEKS = 4;
    private static final int MAX_WEEKS = 52;

    private final NonConformanceRepository repository;

    public GetNcAnalyticsUseCase(NonConformanceRepository repository) {
        this.repository = repository;
    }

    public NcParetoResponse executePareto(int days) {
        if (!ALLOWED_DAYS.contains(days)) {
            throw new IllegalArgumentException("days deve ser 30, 90 ou 180");
        }

        LocalDateTime from = LocalDateTime.now().minusDays(days);
        List<NonConformance> ncs = repository.findAllCreatedAfter(from);

        // Contar por tipo — todos os tipos com 0 se não houver
        Map<String, Long> byType = new LinkedHashMap<>();
        for (NcType type : NcType.values()) {
            byType.put(type.name(), 0L);
        }
        ncs.stream()
                .collect(Collectors.groupingBy(nc -> nc.getType().name(), Collectors.counting()))
                .forEach(byType::put);

        // Contar por severidade — todas as severidades com 0 se não houver
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (NcSeverity sev : NcSeverity.values()) {
            bySeverity.put(sev.name(), 0L);
        }
        ncs.stream()
                .collect(Collectors.groupingBy(nc -> nc.getSeverity().name(), Collectors.counting()))
                .forEach(bySeverity::put);

        return new NcParetoResponse(byType, bySeverity);
    }

    public TimeSeriesResponse executeTrend(int weeks) {
        if (weeks < MIN_WEEKS || weeks > MAX_WEEKS) {
            throw new IllegalArgumentException("weeks deve ser entre " + MIN_WEEKS + " e " + MAX_WEEKS);
        }

        LocalDate today = LocalDate.now();
        LocalDate endDate = today.with(DayOfWeek.SUNDAY);
        LocalDate startDate = endDate.minusWeeks(weeks).with(DayOfWeek.MONDAY);

        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.plusDays(1).atStartOfDay();

        List<NonConformance> ncs = repository.findAllCreatedBetween(from, to);

        // Agrupar por semana ISO
        Map<String, Long> countByWeek = ncs.stream()
                .collect(Collectors.groupingBy(
                        nc -> toIsoWeekLabel(nc.getReportedAt().toLocalDate()),
                        Collectors.counting()
                ));

        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        LocalDate weekStart = startDate;
        for (int i = 0; i < weeks; i++) {
            String label = toIsoWeekLabel(weekStart);
            labels.add(label);
            values.add((double) countByWeek.getOrDefault(label, 0L));
            weekStart = weekStart.plusWeeks(1);
        }

        return new TimeSeriesResponse(labels, values);
    }

    private String toIsoWeekLabel(LocalDate date) {
        int year = date.get(WeekFields.ISO.weekBasedYear());
        int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        return String.format("%d-W%02d", year, week);
    }
}
