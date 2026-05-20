package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.common.kpi.application.OeeCalculator;
import com.industrialhub.backend.oee.application.dto.OeeTrendResponse;
import com.industrialhub.backend.oee.domain.PlannedDowntime;
import com.industrialhub.backend.oee.domain.TimeRecord;
import com.industrialhub.backend.oee.infrastructure.PlannedDowntimeRepository;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GetOeeTrendUseCase {

    private static final int MIN_WEEKS = 4;
    private static final int MAX_WEEKS = 52;

    private final TimeRecordRepository timeRecordRepository;
    private final OeeCalculator oeeCalculator;
    private final PlannedDowntimeRepository plannedDowntimeRepository;

    public GetOeeTrendUseCase(TimeRecordRepository timeRecordRepository,
                               OeeCalculator oeeCalculator,
                               PlannedDowntimeRepository plannedDowntimeRepository) {
        this.timeRecordRepository = timeRecordRepository;
        this.oeeCalculator = oeeCalculator;
        this.plannedDowntimeRepository = plannedDowntimeRepository;
    }

    public OeeTrendResponse execute(int weeks) {
        return execute(weeks, false, null);
    }

    public OeeTrendResponse execute(int weeks, boolean excludePlannedDowntime) {
        return execute(weeks, excludePlannedDowntime, null);
    }

    public OeeTrendResponse execute(int weeks, boolean excludePlannedDowntime, UUID shiftId) {
        if (weeks < MIN_WEEKS || weeks > MAX_WEEKS) {
            throw new IllegalArgumentException("weeks deve ser entre " + MIN_WEEKS + " e " + MAX_WEEKS);
        }

        // Calcular range de datas: últimas N semanas completas
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.with(DayOfWeek.SUNDAY);
        LocalDate startDate = endDate.minusWeeks(weeks).with(DayOfWeek.MONDAY);

        List<TimeRecord> records = shiftId != null
                ? timeRecordRepository.findByProfileDateBetweenAndShiftIdOrderByWorkerIdAscProfileDateAsc(startDate, endDate, shiftId)
                : timeRecordRepository.findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(startDate, endDate);

        // Mapa de paradas planejadas por data (equipment=null = planta inteira)
        Map<LocalDate, Integer> plannedMinutesByDate = Map.of();
        if (excludePlannedDowntime) {
            List<PlannedDowntime> downtimes = plannedDowntimeRepository
                    .findByOptionalFilters(
                        startDate.atStartOfDay(),
                        endDate.plusDays(1).atStartOfDay(),
                        null);
            plannedMinutesByDate = downtimes.stream()
                    .filter(pd -> pd.getEquipment() == null)
                    .collect(Collectors.groupingBy(
                            PlannedDowntime::getDate,
                            Collectors.summingInt(PlannedDowntime::getDurationMinutes)
                    ));
        }

        // Agrupar por semana ISO
        Map<String, List<TimeRecord>> byWeek = records.stream()
                .collect(Collectors.groupingBy(r -> toIsoWeekLabel(r.getProfileDate())));

        // Gerar lista de semanas em ordem
        List<String> weekLabels = new ArrayList<>();
        List<Double> oeeValues = new ArrayList<>();
        List<Integer> sampleCounts = new ArrayList<>();

        final Map<LocalDate, Integer> finalPlannedMinutesByDate = plannedMinutesByDate;

        LocalDate weekStart = startDate;
        for (int i = 0; i < weeks; i++) {
            String label = toIsoWeekLabel(weekStart);
            weekLabels.add(label);

            List<TimeRecord> weekRecords = byWeek.getOrDefault(label, List.of());
            sampleCounts.add(countDistinctDays(weekRecords));

            if (weekRecords.isEmpty()) {
                oeeValues.add(null);
            } else {
                Double oee = oeeCalculator.computeAvg(weekRecords, finalPlannedMinutesByDate);
                oeeValues.add(oee);
            }

            weekStart = weekStart.plusWeeks(1);
        }

        return new OeeTrendResponse(weekLabels, oeeValues, sampleCounts);
    }

    private String toIsoWeekLabel(LocalDate date) {
        int year = date.get(WeekFields.ISO.weekBasedYear());
        int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        return String.format("%d-W%02d", year, week);
    }

    private int countDistinctDays(List<TimeRecord> records) {
        return (int) records.stream()
                .map(TimeRecord::getProfileDate)
                .distinct()
                .count();
    }
}
