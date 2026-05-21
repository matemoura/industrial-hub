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
public class GetOeeBenchmarkByWorkerUseCase {

    private final TimeRecordRepository timeRecordRepository;
    private final BenchmarkCalculator benchmarkCalculator;

    public GetOeeBenchmarkByWorkerUseCase(TimeRecordRepository timeRecordRepository,
                                           BenchmarkCalculator benchmarkCalculator) {
        this.timeRecordRepository = timeRecordRepository;
        this.benchmarkCalculator = benchmarkCalculator;
    }

    @Transactional(readOnly = true)
    public List<BenchmarkResponse> execute(LocalDate from, LocalDate to) {
        benchmarkCalculator.validatePeriod(from, to);

        List<TimeRecord> records = timeRecordRepository
                .findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(from, to);

        if (records.isEmpty()) {
            return List.of();
        }

        // Group by workerName → compute daily OEE per worker → aggregate
        Map<String, List<TimeRecord>> byWorker = records.stream()
                .collect(Collectors.groupingBy(TimeRecord::getWorkerName));

        List<BenchmarkResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<TimeRecord>> entry : byWorker.entrySet()) {
            String workerName = entry.getKey();
            List<Double> dailyOees = benchmarkCalculator.computeDailyOees(entry.getValue());
            if (dailyOees.isEmpty()) continue;
            result.add(benchmarkCalculator.buildResponse(workerName, dailyOees, null));
        }

        result.sort((a, b) -> Double.compare(b.avgOee(), a.avgOee()));
        return result;
    }

}
