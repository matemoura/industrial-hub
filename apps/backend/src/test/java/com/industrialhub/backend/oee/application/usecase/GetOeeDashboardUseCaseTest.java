package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.WorkerOeeDto;
import com.industrialhub.backend.oee.domain.ImportBatch;
import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.domain.TimeRecord;
import com.industrialhub.backend.oee.application.validation.DateRangeValidator;
import com.industrialhub.backend.oee.infrastructure.PlannedDowntimeRepository;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOeeDashboardUseCaseTest {

    @Mock
    private TimeRecordRepository timeRecordRepository;

    @Mock
    private DateRangeValidator dateRangeValidator;

    @Mock
    private PlannedDowntimeRepository plannedDowntimeRepository;

    private GetOeeDashboardUseCase useCase;

    private static final LocalDate DATE = LocalDate.of(2026, 4, 28);
    private static final Long WORKER_ID = 1001L;
    private static final String WORKER_NAME = "João Silva";

    private ImportBatch batch;

    @BeforeEach
    void setUp() {
        batch = ImportBatch.builder().build();
        useCase = new GetOeeDashboardUseCase(timeRecordRepository, dateRangeValidator, plannedDowntimeRepository);
    }

    @Test
    void availability_isProductiveOverShift() {
        // 4h productive, 1h indirect, shift 08:00–17:00 (9h)
        List<TimeRecord> records = List.of(
                record(RecordType.REGISTRO_ENTRADA, dt(8, 0), dt(8, 0), null),
                record(RecordType.PROCESSO, dt(8, 0), dt(12, 0), bd("4.0000")),
                record(RecordType.ATIVIDADE_INDIRETA, dt(13, 0), dt(14, 0), bd("1.0000")),
                record(RecordType.REGISTRO_SAIDA, dt(17, 0), dt(17, 0), null)
        );
        when(timeRecordRepository.findByPeriodAndOptionalWorker(DATE, DATE, null))
                .thenReturn(records);

        List<WorkerOeeDto> result = useCase.execute(DATE, DATE, null);

        assertThat(result).hasSize(1);
        WorkerOeeDto dto = result.getFirst();
        assertThat(dto.workerId()).isEqualTo(WORKER_ID);
        assertThat(dto.productiveHours()).isEqualByComparingTo("4.0000");
        assertThat(dto.indirectHours()).isEqualByComparingTo("1.0000");
        assertThat(dto.shiftDuration()).isEqualByComparingTo("9.0000");
        assertThat(dto.availability()).isEqualByComparingTo("0.4444");
    }

    @Test
    void availability_isNull_whenNoEntryExitPunch() {
        List<TimeRecord> records = List.of(
                record(RecordType.PROCESSO, dt(8, 0), dt(12, 0), bd("4.0000"))
        );
        when(timeRecordRepository.findByPeriodAndOptionalWorker(DATE, DATE, null))
                .thenReturn(records);

        WorkerOeeDto dto = useCase.execute(DATE, DATE, null).getFirst();

        assertThat(dto.shiftDuration()).isNull();
        assertThat(dto.availability()).isNull();
    }

    @Test
    void availability_isZero_whenOnlyIndirectActivities() {
        List<TimeRecord> records = List.of(
                record(RecordType.REGISTRO_ENTRADA, dt(8, 0), dt(8, 0), null),
                record(RecordType.ATIVIDADE_INDIRETA, dt(8, 0), dt(10, 0), bd("2.0000")),
                record(RecordType.REGISTRO_SAIDA, dt(10, 0), dt(10, 0), null)
        );
        when(timeRecordRepository.findByPeriodAndOptionalWorker(DATE, DATE, null))
                .thenReturn(records);

        WorkerOeeDto dto = useCase.execute(DATE, DATE, null).getFirst();

        assertThat(dto.productiveHours()).isEqualByComparingTo("0.0000");
        assertThat(dto.availability()).isEqualByComparingTo("0.0000");
    }

    @Test
    void multipleWorkers_returnedSeparately() {
        List<TimeRecord> records = List.of(
                recordForWorker(2001L, "Maria", RecordType.PROCESSO, dt(8, 0), dt(9, 0), bd("1.0000")),
                recordForWorker(2001L, "Maria", RecordType.REGISTRO_ENTRADA, dt(8, 0), dt(8, 0), null),
                recordForWorker(2001L, "Maria", RecordType.REGISTRO_SAIDA, dt(17, 0), dt(17, 0), null),
                record(RecordType.PROCESSO, dt(8, 0), dt(12, 0), bd("4.0000")),
                record(RecordType.REGISTRO_ENTRADA, dt(8, 0), dt(8, 0), null),
                record(RecordType.REGISTRO_SAIDA, dt(17, 0), dt(17, 0), null)
        );
        when(timeRecordRepository.findByPeriodAndOptionalWorker(DATE, DATE, null))
                .thenReturn(records);

        List<WorkerOeeDto> result = useCase.execute(DATE, DATE, null);

        assertThat(result).hasSize(2);
    }

    @Test
    void emptyPeriod_returnsEmptyList() {
        when(timeRecordRepository.findByPeriodAndOptionalWorker(DATE, DATE, null))
                .thenReturn(List.of());

        assertThat(useCase.execute(DATE, DATE, null)).isEmpty();
    }

    // --- helpers ---

    private TimeRecord record(RecordType type, LocalDateTime start, LocalDateTime end, BigDecimal hours) {
        return recordForWorker(WORKER_ID, WORKER_NAME, type, start, end, hours);
    }

    private TimeRecord recordForWorker(Long wId, String wName, RecordType type,
                                       LocalDateTime start, LocalDateTime end, BigDecimal hours) {
        return TimeRecord.builder()
                .batch(batch)
                .workerId(wId)
                .workerName(wName)
                .profileDate(DATE)
                .recordType(type)
                .startTime(start)
                .endTime(end)
                .hours(hours)
                .build();
    }

    private LocalDateTime dt(int hour, int minute) {
        return DATE.atTime(hour, minute);
    }

    private BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
