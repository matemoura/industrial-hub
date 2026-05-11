package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.ProcessEfficiencyDto;
import com.industrialhub.backend.oee.application.validation.DateRangeValidator;
import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.infrastructure.ProcessSummary;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProcessEfficiencyUseCaseTest {

    @Mock
    private TimeRecordRepository repository;

    @Mock
    private DateRangeValidator dateRangeValidator;

    @InjectMocks
    private GetProcessEfficiencyUseCase useCase;

    private static final LocalDate START = LocalDate.of(2026, 4, 1);
    private static final LocalDate END   = LocalDate.of(2026, 4, 30);

    @Test
    void validRange_returnsCorrectAggregation() {
        when(repository.findProcessSummary(START, END, null, RecordType.PROCESSO))
                .thenReturn(List.of(
                        summary("Montagem Fibra Laser", "12.5000", 3L, 8L),
                        summary("Solda TIG",            "4.0000",  2L, 4L)
                ));

        List<ProcessEfficiencyDto> result = useCase.execute(START, END, null);

        assertThat(result).hasSize(2);

        ProcessEfficiencyDto first = result.get(0);
        assertThat(first.description()).isEqualTo("Montagem Fibra Laser");
        assertThat(first.totalHours()).isEqualByComparingTo("12.5000");
        assertThat(first.workerCount()).isEqualTo(3L);
        assertThat(first.occurrences()).isEqualTo(8L);

        ProcessEfficiencyDto second = result.get(1);
        assertThat(second.description()).isEqualTo("Solda TIG");
        assertThat(second.totalHours()).isEqualByComparingTo("4.0000");
    }

    @Test
    void filteredByWorkerId_passedToRepository() {
        when(repository.findProcessSummary(START, END, 1001L, RecordType.PROCESSO))
                .thenReturn(List.of(summary("Calibração", "2.0000", 1L, 2L)));

        List<ProcessEfficiencyDto> result = useCase.execute(START, END, 1001L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().description()).isEqualTo("Calibração");
        assertThat(result.getFirst().workerCount()).isEqualTo(1L);
    }

    @Test
    void emptyRange_returnsEmptyList() {
        when(repository.findProcessSummary(START, END, null, RecordType.PROCESSO))
                .thenReturn(List.of());

        assertThat(useCase.execute(START, END, null)).isEmpty();
    }

    @Test
    void nullTotalHours_treatedAsZero() {
        when(repository.findProcessSummary(START, END, null, RecordType.PROCESSO))
                .thenReturn(List.of(summary("Inspeção", null, 1L, 1L)));

        ProcessEfficiencyDto dto = useCase.execute(START, END, null).getFirst();

        assertThat(dto.totalHours()).isEqualByComparingTo("0.0000");
    }

    // --- helper ---

    private ProcessSummary summary(String description, String totalHours,
                                   Long workerCount, Long occurrences) {
        return new ProcessSummary() {
            public String getDescription() { return description; }
            public BigDecimal getTotalHours() {
                return totalHours != null ? new BigDecimal(totalHours) : null;
            }
            public Long getWorkerCount()  { return workerCount; }
            public Long getOccurrences()  { return occurrences; }
        };
    }
}
