package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.WorkerOeeDto;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GetOeeDashboardUseCase {

    private final TimeRecordRepository timeRecordRepository;

    public GetOeeDashboardUseCase(TimeRecordRepository timeRecordRepository) {
        this.timeRecordRepository = timeRecordRepository;
    }

    @Transactional(readOnly = true)
    public List<WorkerOeeDto> execute(LocalDate startDate, LocalDate endDate, Long workerId) {
        List<TimeRecord> records = timeRecordRepository
                .findByPeriodAndOptionalWorker(startDate, endDate, workerId);

        // Group by (workerId, profileDate)
        Map<String, List<TimeRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getWorkerId() + "|" + r.getProfileDate()
                ));

        return grouped.values().stream()
                .map(this::toOeeDto)
                .sorted(Comparator.comparing(WorkerOeeDto::date)
                        .thenComparing(WorkerOeeDto::workerId))
                .toList();
    }

    private WorkerOeeDto toOeeDto(List<TimeRecord> dayRecords) {
        TimeRecord first = dayRecords.getFirst();
        Long wId = first.getWorkerId();
        String wName = first.getWorkerName();
        LocalDate date = first.getProfileDate();

        BigDecimal productiveHours = sumHours(dayRecords, RecordType.PROCESSO);
        BigDecimal indirectHours = sumHours(dayRecords, RecordType.ATIVIDADE_INDIRETA);
        BigDecimal shiftDuration = computeShiftDuration(dayRecords);
        BigDecimal availability = computeAvailability(productiveHours, shiftDuration);

        return new WorkerOeeDto(wId, wName, date, productiveHours, indirectHours, shiftDuration, availability);
    }

    private BigDecimal sumHours(List<TimeRecord> records, RecordType type) {
        return records.stream()
                .filter(r -> r.getRecordType() == type)
                .map(r -> r.getHours() != null ? r.getHours() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal computeShiftDuration(List<TimeRecord> records) {
        LocalDateTime entryTime = records.stream()
                .filter(r -> r.getRecordType() == RecordType.REGISTRO_ENTRADA)
                .map(TimeRecord::getStartTime)
                .filter(t -> t != null)
                .min(Comparator.naturalOrder())
                .orElse(null);

        LocalDateTime exitTime = records.stream()
                .filter(r -> r.getRecordType() == RecordType.REGISTRO_SAIDA)
                .map(r -> r.getEndTime() != null ? r.getEndTime() : r.getStartTime())
                .filter(t -> t != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        if (entryTime == null || exitTime == null || !exitTime.isAfter(entryTime)) {
            return null;
        }

        long minutes = Duration.between(entryTime, exitTime).toMinutes();
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal computeAvailability(BigDecimal productiveHours, BigDecimal shiftDuration) {
        if (shiftDuration == null || shiftDuration.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return productiveHours
                .divide(shiftDuration, 4, RoundingMode.HALF_UP);
    }
}
