package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.common.kpi.application.OeeCalculator;
import com.industrialhub.backend.oee.application.dto.OeeTrendResponse;
import com.industrialhub.backend.oee.domain.ImportBatch;
import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.domain.TimeRecord;
import com.industrialhub.backend.oee.infrastructure.PlannedDowntimeRepository;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOeeTrendUseCaseTest {

    @Mock private TimeRecordRepository timeRecordRepository;

    @Mock private PlannedDowntimeRepository plannedDowntimeRepository;

    private OeeCalculator oeeCalculator;
    private GetOeeTrendUseCase useCase;

    @BeforeEach
    void setUp() {
        oeeCalculator = new OeeCalculator();
        useCase = new GetOeeTrendUseCase(timeRecordRepository, oeeCalculator, plannedDowntimeRepository);
    }

    @Test
    void shouldThrow_whenWeeksLessThanMinimum() {
        assertThatThrownBy(() -> useCase.execute(3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weeks deve ser entre 4 e 52");
    }

    @Test
    void shouldThrow_whenWeeksGreaterThanMaximum() {
        assertThatThrownBy(() -> useCase.execute(53))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weeks deve ser entre 4 e 52");
    }

    @Test
    void shouldReturnNullOeeValue_forWeekWithoutData() {
        when(timeRecordRepository.findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(any(), any()))
                .thenReturn(List.of());

        OeeTrendResponse response = useCase.execute(4);

        assertThat(response.weekLabels()).hasSize(4);
        assertThat(response.oeeValues()).hasSize(4);
        assertThat(response.sampleCounts()).hasSize(4);
        assertThat(response.oeeValues()).containsOnly((Double) null);
        assertThat(response.sampleCounts()).containsOnly(0);
    }

    @Test
    void shouldReturnCorrectWeekCount() {
        when(timeRecordRepository.findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(any(), any()))
                .thenReturn(List.of());

        OeeTrendResponse response = useCase.execute(12);

        assertThat(response.weekLabels()).hasSize(12);
        assertThat(response.oeeValues()).hasSize(12);
    }

    @Test
    void shouldReturnIsoWeekLabels_inCorrectFormat() {
        when(timeRecordRepository.findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(any(), any()))
                .thenReturn(List.of());

        OeeTrendResponse response = useCase.execute(4);

        // All labels should match "YYYY-Www" format
        response.weekLabels().forEach(label ->
                assertThat(label).matches("\\d{4}-W\\d{2}"));
    }

    @Test
    void shouldReturnOeeValue_forWeekWithData() {
        // Create a time record from the start of the current period
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.with(DayOfWeek.SUNDAY);
        LocalDate weekStart = endDate.minusWeeks(4).with(DayOfWeek.MONDAY);

        // Use a date from the first week of the range
        LocalDate recordDate = weekStart;

        ImportBatch batch = ImportBatch.builder().id(UUID.randomUUID()).build();

        TimeRecord entry = TimeRecord.builder()
                .id(UUID.randomUUID())
                .batch(batch)
                .workerId(1L)
                .workerName("Worker 1")
                .profileDate(recordDate)
                .startTime(recordDate.atTime(8, 0))
                .endTime(recordDate.atTime(8, 0))
                .recordType(RecordType.REGISTRO_ENTRADA)
                .hours(BigDecimal.ZERO)
                .build();

        TimeRecord exit = TimeRecord.builder()
                .id(UUID.randomUUID())
                .batch(batch)
                .workerId(1L)
                .workerName("Worker 1")
                .profileDate(recordDate)
                .startTime(recordDate.atTime(16, 0))
                .endTime(recordDate.atTime(16, 0))
                .recordType(RecordType.REGISTRO_SAIDA)
                .hours(BigDecimal.ZERO)
                .build();

        TimeRecord process = TimeRecord.builder()
                .id(UUID.randomUUID())
                .batch(batch)
                .workerId(1L)
                .workerName("Worker 1")
                .profileDate(recordDate)
                .recordType(RecordType.PROCESSO)
                .hours(new BigDecimal("6.0"))
                .build();

        when(timeRecordRepository.findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(any(), any()))
                .thenReturn(List.of(entry, exit, process));

        OeeTrendResponse response = useCase.execute(4);

        // At least one week should have a non-null OEE value
        assertThat(response.oeeValues()).anyMatch(v -> v != null);
        assertThat(response.sampleCounts()).anyMatch(c -> c > 0);
    }

    // US-056: shiftId nulo retorna todos os registros (via método sem filtro)
    @Test
    void shouldCallNonShiftQuery_whenShiftIdIsNull() {
        when(timeRecordRepository.findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(any(), any()))
                .thenReturn(List.of());

        OeeTrendResponse response = useCase.execute(4, false, null);

        assertThat(response.weekLabels()).hasSize(4);
        // deve ter chamado o método sem shiftId
        org.mockito.Mockito.verify(timeRecordRepository)
                .findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(any(), any());
    }

    // US-056: shiftId informado chama query com filtro de turno
    @Test
    void shouldCallShiftFilteredQuery_whenShiftIdIsProvided() {
        UUID shiftId = UUID.randomUUID();
        when(timeRecordRepository.findByProfileDateBetweenAndShiftIdOrderByWorkerIdAscProfileDateAsc(any(), any(), any()))
                .thenReturn(List.of());

        OeeTrendResponse response = useCase.execute(4, false, shiftId);

        assertThat(response.weekLabels()).hasSize(4);
        org.mockito.Mockito.verify(timeRecordRepository)
                .findByProfileDateBetweenAndShiftIdOrderByWorkerIdAscProfileDateAsc(any(), any(), any());
    }
}
