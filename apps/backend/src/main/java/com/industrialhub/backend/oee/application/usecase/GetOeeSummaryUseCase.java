package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.PeriodSummaryDto;
import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.domain.TimeRecord;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GetOeeSummaryUseCase {

    private final TimeRecordRepository timeRecordRepository;

    public GetOeeSummaryUseCase(TimeRecordRepository timeRecordRepository) {
        this.timeRecordRepository = timeRecordRepository;
    }

    @Transactional(readOnly = true)
    public List<PeriodSummaryDto> execute(LocalDate startDate, LocalDate endDate, GroupBy groupBy) {
        List<TimeRecord> records = timeRecordRepository
                .findByPeriodAndOptionalWorker(startDate, endDate, null);

        // Step 1: availability per worker per day
        Map<String, List<TimeRecord>> byWorkerDay = records.stream()
                .collect(Collectors.groupingBy(r -> r.getWorkerId() + "|" + r.getProfileDate()));

        List<WorkerDay> workerDays = byWorkerDay.values().stream()
                .map(this::toWorkerDay)
                .toList();

        // Step 2: group by period key
        Map<String, List<WorkerDay>> byPeriod = workerDays.stream()
                .collect(Collectors.groupingBy(w -> periodKey(w.date(), groupBy)));

        return byPeriod.entrySet().stream()
                .map(e -> toPeriodSummary(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(PeriodSummaryDto::period))
                .toList();
    }

    private WorkerDay toWorkerDay(List<TimeRecord> dayRecords) {
        TimeRecord first = dayRecords.getFirst();
        Long workerId = first.getWorkerId();
        LocalDate date = first.getProfileDate();

        BigDecimal productiveHours = sumHours(dayRecords, RecordType.PROCESSO);
        BigDecimal shiftDuration = computeShiftDuration(dayRecords);
        BigDecimal availability = (shiftDuration == null || shiftDuration.compareTo(BigDecimal.ZERO) == 0)
                ? null
                : productiveHours.divide(shiftDuration, 4, RoundingMode.HALF_UP);

        return new WorkerDay(workerId, date, availability);
    }

    private PeriodSummaryDto toPeriodSummary(String period, List<WorkerDay> days) {
        int workerCount = (int) days.stream().map(WorkerDay::workerId).distinct().count();

        List<BigDecimal> validAvailabilities = days.stream()
                .map(WorkerDay::availability)
                .filter(Objects::nonNull)
                .toList();

        BigDecimal avgAvailability = validAvailabilities.isEmpty() ? null
                : validAvailabilities.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(validAvailabilities.size()), 4, RoundingMode.HALF_UP);

        return new PeriodSummaryDto(period, avgAvailability, workerCount);
    }

    private String periodKey(LocalDate date, GroupBy groupBy) {
        return switch (groupBy) {
            case DAY   -> date.toString();
            case WEEK  -> {
                int year = date.get(WeekFields.ISO.weekBasedYear());
                int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
                yield String.format("%d-W%02d", year, week);
            }
            case MONTH -> String.format("%d-%02d", date.getYear(), date.getMonthValue());
        };
    }

    private BigDecimal sumHours(List<TimeRecord> records, RecordType type) {
        return records.stream()
                .filter(r -> r.getRecordType() == type)
                .map(r -> r.getHours() != null ? r.getHours() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal computeShiftDuration(List<TimeRecord> records) {
        LocalDateTime entryTime = records.stream()
                .filter(r -> r.getRecordType() == RecordType.REGISTRO_ENTRADA)
                .map(TimeRecord::getStartTime)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);

        LocalDateTime exitTime = records.stream()
                .filter(r -> r.getRecordType() == RecordType.REGISTRO_SAIDA)
                .map(r -> r.getEndTime() != null ? r.getEndTime() : r.getStartTime())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        if (entryTime == null || exitTime == null || !exitTime.isAfter(entryTime)) return null;

        return BigDecimal.valueOf(Duration.between(entryTime, exitTime).toMinutes())
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
    }

    private record WorkerDay(Long workerId, LocalDate date, BigDecimal availability) {}
}
