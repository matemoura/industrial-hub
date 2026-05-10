package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.IndirectActivityDto;
import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.infrastructure.IndirectActivitySummary;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class GetIndirectActivitiesUseCase {

    private final TimeRecordRepository timeRecordRepository;

    public GetIndirectActivitiesUseCase(TimeRecordRepository timeRecordRepository) {
        this.timeRecordRepository = timeRecordRepository;
    }

    @Transactional(readOnly = true)
    public List<IndirectActivityDto> execute(LocalDate startDate, LocalDate endDate, Long workerId) {
        List<IndirectActivitySummary> summaries = timeRecordRepository
                .findActivitySummaryByType(startDate, endDate, workerId, RecordType.ATIVIDADE_INDIRETA);

        BigDecimal grandTotal = summaries.stream()
                .map(s -> s.getTotalHours() != null ? s.getTotalHours() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return summaries.stream()
                .map(s -> toDto(s, grandTotal))
                .toList();
    }

    private IndirectActivityDto toDto(IndirectActivitySummary s, BigDecimal grandTotal) {
        BigDecimal totalHours = s.getTotalHours() != null ? s.getTotalHours() : BigDecimal.ZERO;
        BigDecimal percent = grandTotal.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : totalHours.divide(grandTotal, 4, RoundingMode.HALF_UP);

        return new IndirectActivityDto(
                s.getDescription(),
                s.getOccurrences() != null ? s.getOccurrences() : 0L,
                totalHours.setScale(4, RoundingMode.HALF_UP),
                percent
        );
    }
}
