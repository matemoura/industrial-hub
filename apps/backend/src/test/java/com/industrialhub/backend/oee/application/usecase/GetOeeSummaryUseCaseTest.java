package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.PeriodSummaryDto;
import com.industrialhub.backend.oee.domain.ImportBatch;
import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.domain.TimeRecord;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOeeSummaryUseCaseTest {

    @Mock
    private TimeRecordRepository timeRecordRepository;

    @InjectMocks
    private GetOeeSummaryUseCase useCase;

    private ImportBatch batch;

    private static final LocalDate D1 = LocalDate.of(2026, 4, 28); // Tuesday W18
    private static final LocalDate D2 = LocalDate.of(2026, 4, 29); // Wednesday W18

    @BeforeEach
    void setUp() {
        batch = ImportBatch.builder().build();
    }

    @Test
    void groupByDay_returnsOneEntryPerDay() {
        when(timeRecordRepository.findByPeriodAndOptionalWorker(D1, D2, null))
                .thenReturn(recordsForTwoDays());

        List<PeriodSummaryDto> result = useCase.execute(D1, D2, GroupBy.DAY);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).period()).isEqualTo("2026-04-28");
        assertThat(result.get(1).period()).isEqualTo("2026-04-29");
    }

    @Test
    void groupByWeek_mergesBothDaysIntoSameWeek() {
        when(timeRecordRepository.findByPeriodAndOptionalWorker(D1, D2, null))
                .thenReturn(recordsForTwoDays());

        List<PeriodSummaryDto> result = useCase.execute(D1, D2, GroupBy.WEEK);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().period()).isEqualTo("2026-W18");
    }

    @Test
    void groupByMonth_mergesBothDaysIntoSameMonth() {
        when(timeRecordRepository.findByPeriodAndOptionalWorker(D1, D2, null))
                .thenReturn(recordsForTwoDays());

        List<PeriodSummaryDto> result = useCase.execute(D1, D2, GroupBy.MONTH);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().period()).isEqualTo("2026-04");
    }

    @Test
    void workersWithoutShift_excludedFromAvgAvailability_butCountedInWorkerCount() {
        // Worker 1: has punch in/out → availability = 4/8 = 0.5
        // Worker 2: no punch → availability = null → excluded from avg
        List<TimeRecord> records = List.of(
                record(1001L, D1, RecordType.REGISTRO_ENTRADA, dt(D1, 8, 0), dt(D1, 8, 0), null),
                record(1001L, D1, RecordType.PROCESSO,          dt(D1, 8, 0), dt(D1, 12, 0), bd("4.0")),
                record(1001L, D1, RecordType.REGISTRO_SAIDA,    dt(D1, 16, 0), dt(D1, 16, 0), null),
                record(2001L, D1, RecordType.PROCESSO,          dt(D1, 9, 0), dt(D1, 10, 0), bd("1.0"))
        );
        when(timeRecordRepository.findByPeriodAndOptionalWorker(D1, D1, null))
                .thenReturn(records);

        PeriodSummaryDto dto = useCase.execute(D1, D1, GroupBy.DAY).getFirst();

        assertThat(dto.workerCount()).isEqualTo(2);
        assertThat(dto.avgAvailability()).isEqualByComparingTo("0.5000"); // only worker 1
    }

    @Test
    void allWorkersWithoutShift_avgAvailabilityIsNull() {
        List<TimeRecord> records = List.of(
                record(1001L, D1, RecordType.PROCESSO, dt(D1, 8, 0), dt(D1, 12, 0), bd("4.0"))
        );
        when(timeRecordRepository.findByPeriodAndOptionalWorker(D1, D1, null))
                .thenReturn(records);

        PeriodSummaryDto dto = useCase.execute(D1, D1, GroupBy.DAY).getFirst();

        assertThat(dto.avgAvailability()).isNull();
        assertThat(dto.workerCount()).isEqualTo(1);
    }

    @Test
    void emptyPeriod_returnsEmptyList() {
        when(timeRecordRepository.findByPeriodAndOptionalWorker(D1, D2, null))
                .thenReturn(List.of());

        assertThat(useCase.execute(D1, D2, GroupBy.DAY)).isEmpty();
    }

    // --- helpers ---

    private List<TimeRecord> recordsForTwoDays() {
        return List.of(
                record(1001L, D1, RecordType.REGISTRO_ENTRADA, dt(D1, 8, 0), dt(D1, 8, 0), null),
                record(1001L, D1, RecordType.PROCESSO,          dt(D1, 8, 0), dt(D1, 12, 0), bd("4.0")),
                record(1001L, D1, RecordType.REGISTRO_SAIDA,    dt(D1, 16, 0), dt(D1, 16, 0), null),
                record(1001L, D2, RecordType.REGISTRO_ENTRADA, dt(D2, 8, 0), dt(D2, 8, 0), null),
                record(1001L, D2, RecordType.PROCESSO,          dt(D2, 8, 0), dt(D2, 10, 0), bd("2.0")),
                record(1001L, D2, RecordType.REGISTRO_SAIDA,    dt(D2, 16, 0), dt(D2, 16, 0), null)
        );
    }

    private TimeRecord record(Long workerId, LocalDate date, RecordType type,
                              LocalDateTime start, LocalDateTime end, BigDecimal hours) {
        return TimeRecord.builder()
                .batch(batch)
                .workerId(workerId)
                .workerName("Worker " + workerId)
                .profileDate(date)
                .recordType(type)
                .startTime(start)
                .endTime(end)
                .hours(hours)
                .build();
    }

    private LocalDateTime dt(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute);
    }

    private BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
