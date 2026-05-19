package com.industrialhub.backend.common.kpi.application;

import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.domain.TimeRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class OeeCalculator {

    public Double computeAvg(List<TimeRecord> records) {
        return computeAvg(records, Map.of());
    }

    /**
     * Computa OEE médio com suporte a subtração de paradas planejadas.
     *
     * @param records            registros de tempo
     * @param plannedMinutesByDate mapa de LocalDate → minutos de parada planejada (planta inteira)
     */
    public Double computeAvg(List<TimeRecord> records, Map<LocalDate, Integer> plannedMinutesByDate) {
        if (records.isEmpty()) return null;

        Map<String, List<TimeRecord>> byWorkerDay = records.stream()
                .collect(Collectors.groupingBy(r -> r.getWorkerId() + "|" + r.getProfileDate()));

        List<Double> availabilities = byWorkerDay.values().stream()
                .map(dayRecords -> availabilityForDay(dayRecords, plannedMinutesByDate))
                .filter(Objects::nonNull)
                .toList();

        if (availabilities.isEmpty()) return null;
        return availabilities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private Double availabilityForDay(List<TimeRecord> dayRecords,
                                       Map<LocalDate, Integer> plannedMinutesByDate) {
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
        double shiftMinutes = Duration.between(entry, exit).toMinutes();
        if (shiftMinutes == 0) return null;

        // Subtrair paradas planejadas do denominador (fallback se resultado <= 0)
        LocalDate date = dayRecords.getFirst().getProfileDate();
        int plannedMinutes = plannedMinutesByDate.getOrDefault(date, 0);
        double effectiveMinutes = shiftMinutes - plannedMinutes;
        if (effectiveMinutes <= 0) {
            effectiveMinutes = shiftMinutes;
        }

        double shiftHours = effectiveMinutes / 60.0;
        double availability = productive / shiftHours;
        // Cap de 1.0
        return Math.min(availability, 1.0);
    }
}
