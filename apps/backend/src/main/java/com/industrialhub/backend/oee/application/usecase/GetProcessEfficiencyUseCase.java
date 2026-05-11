package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.ProcessEfficiencyDto;
import com.industrialhub.backend.oee.application.validation.DateRangeValidator;
import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.infrastructure.ProcessSummary;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class GetProcessEfficiencyUseCase {

    private final TimeRecordRepository repository;
    private final DateRangeValidator dateRangeValidator;

    public GetProcessEfficiencyUseCase(TimeRecordRepository repository,
                                       DateRangeValidator dateRangeValidator) {
        this.repository = repository;
        this.dateRangeValidator = dateRangeValidator;
    }

    @Transactional(readOnly = true)
    public List<ProcessEfficiencyDto> execute(LocalDate startDate, LocalDate endDate, Long workerId) {
        dateRangeValidator.validate(startDate, endDate);
        return repository.findProcessSummary(startDate, endDate, workerId, RecordType.PROCESSO)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private ProcessEfficiencyDto toDto(ProcessSummary s) {
        BigDecimal totalHours = s.getTotalHours() != null
                ? s.getTotalHours().setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4);
        long workerCount = s.getWorkerCount() != null ? s.getWorkerCount() : 0L;
        long occurrences = s.getOccurrences() != null ? s.getOccurrences() : 0L;
        return new ProcessEfficiencyDto(s.getDescription(), totalHours, workerCount, occurrences);
    }
}
