package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.IndirectActivityDto;
import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.infrastructure.IndirectActivitySummary;
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
class GetIndirectActivitiesUseCaseTest {

    @Mock
    private TimeRecordRepository timeRecordRepository;

    @InjectMocks
    private GetIndirectActivitiesUseCase useCase;

    private static final LocalDate START = LocalDate.of(2026, 4, 28);
    private static final LocalDate END   = LocalDate.of(2026, 4, 28);

    @Test
    void percentOfTotal_calculatedCorrectly() {
        when(timeRecordRepository.findActivitySummaryByType(START, END, null, RecordType.ATIVIDADE_INDIRETA))
                .thenReturn(List.of(
                        summary("Aguardando alocação", 3L, "3.0000"),
                        summary("Reunião",             1L, "1.0000")
                ));

        List<IndirectActivityDto> result = useCase.execute(START, END, null);

        assertThat(result).hasSize(2);
        IndirectActivityDto first = result.get(0);
        assertThat(first.description()).isEqualTo("Aguardando alocação");
        assertThat(first.totalHours()).isEqualByComparingTo("3.0000");
        assertThat(first.occurrences()).isEqualTo(3L);
        assertThat(first.percentOfTotal()).isEqualByComparingTo("0.7500"); // 3/4

        assertThat(result.get(1).percentOfTotal()).isEqualByComparingTo("0.2500"); // 1/4
    }

    @Test
    void emptyPeriod_returnsEmptyList() {
        when(timeRecordRepository.findActivitySummaryByType(START, END, null, RecordType.ATIVIDADE_INDIRETA))
                .thenReturn(List.of());

        assertThat(useCase.execute(START, END, null)).isEmpty();
    }

    @Test
    void singleActivity_percentIs100() {
        when(timeRecordRepository.findActivitySummaryByType(START, END, null, RecordType.ATIVIDADE_INDIRETA))
                .thenReturn(List.of(summary("Manutenção", 2L, "2.5000")));

        IndirectActivityDto dto = useCase.execute(START, END, null).getFirst();

        assertThat(dto.percentOfTotal()).isEqualByComparingTo("1.0000");
    }

    @Test
    void workerIdFilter_passedToRepository() {
        when(timeRecordRepository.findActivitySummaryByType(START, END, 1001L, RecordType.ATIVIDADE_INDIRETA))
                .thenReturn(List.of(summary("Treinamento", 1L, "1.0000")));

        List<IndirectActivityDto> result = useCase.execute(START, END, 1001L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().description()).isEqualTo("Treinamento");
    }

    @Test
    void nullTotalHours_treatedAsZero() {
        when(timeRecordRepository.findActivitySummaryByType(START, END, null, RecordType.ATIVIDADE_INDIRETA))
                .thenReturn(List.of(summary("Pausa", 1L, null)));

        IndirectActivityDto dto = useCase.execute(START, END, null).getFirst();

        assertThat(dto.totalHours()).isEqualByComparingTo("0.0000");
        assertThat(dto.percentOfTotal()).isEqualByComparingTo("0.0000");
    }

    // --- helper ---

    private IndirectActivitySummary summary(String description, Long occurrences, String totalHours) {
        return new IndirectActivitySummary() {
            public String getDescription() { return description; }
            public Long getOccurrences()   { return occurrences; }
            public BigDecimal getTotalHours() {
                return totalHours != null ? new BigDecimal(totalHours) : null;
            }
        };
    }
}
