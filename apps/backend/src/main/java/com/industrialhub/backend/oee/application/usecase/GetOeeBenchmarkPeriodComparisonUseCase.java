package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.BenchmarkResponse;
import com.industrialhub.backend.oee.application.dto.PeriodComparisonResponse;
import com.industrialhub.backend.oee.domain.TimeRecord;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GetOeeBenchmarkPeriodComparisonUseCase {

    private final TimeRecordRepository timeRecordRepository;
    private final BenchmarkCalculator benchmarkCalculator;

    public GetOeeBenchmarkPeriodComparisonUseCase(TimeRecordRepository timeRecordRepository,
                                                   BenchmarkCalculator benchmarkCalculator) {
        this.timeRecordRepository = timeRecordRepository;
        this.benchmarkCalculator = benchmarkCalculator;
    }

    @Transactional(readOnly = true)
    public PeriodComparisonResponse execute(LocalDate from, LocalDate to,
                                             LocalDate fromB, LocalDate toB) {
        benchmarkCalculator.validatePeriod(from, to);
        benchmarkCalculator.validatePeriod(fromB, toB);

        List<BenchmarkResponse> periodA = computeWeeklyBenchmarks(from, to);
        List<BenchmarkResponse> periodB = computeWeeklyBenchmarks(fromB, toB);

        Double improvementPct;
        if (periodA.isEmpty() || periodB.isEmpty()) {
            improvementPct = null;
        } else {
            double avgA = periodA.stream().mapToDouble(BenchmarkResponse::avgOee).average().orElse(0);
            double avgB = periodB.stream().mapToDouble(BenchmarkResponse::avgOee).average().orElse(0);
            improvementPct = avgA == 0 ? null : ((avgB - avgA) / avgA) * 100;
        }

        return new PeriodComparisonResponse(periodA, periodB, improvementPct);
    }

    private List<BenchmarkResponse> computeWeeklyBenchmarks(LocalDate from, LocalDate to) {
        List<TimeRecord> records = timeRecordRepository
                .findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(from, to);

        if (records.isEmpty()) {
            return List.of();
        }

        // Agrupa registros por semana ISO
        Map<String, List<TimeRecord>> byWeek = records.stream()
                .collect(Collectors.groupingBy(r -> toIsoWeekLabel(r.getProfileDate())));

        List<BenchmarkResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<TimeRecord>> entry : byWeek.entrySet()) {
            String weekLabel = entry.getKey();
            List<Double> dailyOees = benchmarkCalculator.computeDailyOees(entry.getValue());
            if (dailyOees.isEmpty()) continue;
            result.add(benchmarkCalculator.buildResponse(weekLabel, dailyOees, null));
        }

        // Ordena por label (semana ISO ordena lexicograficamente: 2026-W01, 2026-W02, ...)
        result.sort(Comparator.comparing(BenchmarkResponse::label));
        return result;
    }

    private String toIsoWeekLabel(LocalDate date) {
        int year = date.get(WeekFields.ISO.weekBasedYear());
        int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        return String.format("%d-W%02d", year, week);
    }
}
