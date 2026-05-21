package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.BenchmarkResponse;
import com.industrialhub.backend.oee.domain.TimeRecord;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GetOeeBenchmarkByShiftUseCase {

    private static final String NO_SHIFT_LABEL = "Sem turno";

    private final TimeRecordRepository timeRecordRepository;
    private final BenchmarkCalculator benchmarkCalculator;

    public GetOeeBenchmarkByShiftUseCase(TimeRecordRepository timeRecordRepository,
                                          BenchmarkCalculator benchmarkCalculator) {
        this.timeRecordRepository = timeRecordRepository;
        this.benchmarkCalculator = benchmarkCalculator;
    }

    @Transactional(readOnly = true)
    public List<BenchmarkResponse> execute(LocalDate from, LocalDate to) {
        benchmarkCalculator.validatePeriod(from, to);

        List<TimeRecord> allRecords = timeRecordRepository
                .findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(from, to);

        if (allRecords.isEmpty()) {
            return List.of();
        }

        // Contar registros sem turno para o campo recordsWithoutShift
        long recordsWithoutShift = allRecords.stream()
                .filter(r -> r.getBatch() == null || r.getBatch().getShift() == null)
                .count();

        // Agrupar por nome do turno (COALESCE: null → "Sem turno")
        Map<String, List<TimeRecord>> byShift = allRecords.stream()
                .collect(Collectors.groupingBy(r -> {
                    if (r.getBatch() == null || r.getBatch().getShift() == null) {
                        return NO_SHIFT_LABEL;
                    }
                    return r.getBatch().getShift().getName();
                }));

        List<BenchmarkResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<TimeRecord>> entry : byShift.entrySet()) {
            String shiftName = entry.getKey();
            List<Double> dailyOees = benchmarkCalculator.computeDailyOees(entry.getValue());
            if (dailyOees.isEmpty()) continue;

            // recordsWithoutShift só é relevante para o grupo "Sem turno"
            Long shiftRecordsWithout = NO_SHIFT_LABEL.equals(shiftName) ? recordsWithoutShift : null;
            result.add(benchmarkCalculator.buildResponse(shiftName, dailyOees, shiftRecordsWithout));
        }

        result.sort((a, b) -> Double.compare(b.avgOee(), a.avgOee()));
        return result;
    }

}
