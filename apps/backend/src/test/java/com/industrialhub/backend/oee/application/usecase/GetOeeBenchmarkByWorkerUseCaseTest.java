package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.common.kpi.application.OeeCalculator;
import com.industrialhub.backend.oee.application.dto.BenchmarkResponse;
import com.industrialhub.backend.oee.domain.ImportBatch;
import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.domain.TimeRecord;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOeeBenchmarkByWorkerUseCaseTest {

    @Mock
    private TimeRecordRepository timeRecordRepository;

    private GetOeeBenchmarkByWorkerUseCase useCase;

    private static final LocalDate FROM = LocalDate.of(2026, 4, 1);
    private static final LocalDate TO   = LocalDate.of(2026, 4, 30);

    @BeforeEach
    void setUp() {
        OeeCalculator oeeCalculator = new OeeCalculator();
        BenchmarkCalculator benchmarkCalculator = new BenchmarkCalculator(oeeCalculator);
        useCase = new GetOeeBenchmarkByWorkerUseCase(timeRecordRepository, benchmarkCalculator);
    }

    // ── Caso 1: Período válido → lista ordenada por avgOee DESC ──────────────

    @Test
    void shouldReturnRankingOrderedByAvgOeeDesc_whenDataExists() {
        // Worker A: alta disponibilidade (4h produtivas / 8h turno = 0.5)
        // Worker B: baixa disponibilidade (2h produtivas / 8h turno = 0.25)
        ImportBatch batch = ImportBatch.builder().build();
        List<TimeRecord> records = List.of(
            // Worker A — dia 2026-04-01
            entry(1L, "Alice", FROM, ldt(8, 0)),
            process(1L, "Alice", FROM, bd("4.0")),
            exit(1L, "Alice", FROM, ldt(16, 0)),
            // Worker B — dia 2026-04-01
            entry(2L, "Bob", FROM, ldt(8, 0)),
            process(2L, "Bob", FROM, bd("2.0")),
            exit(2L, "Bob", FROM, ldt(16, 0))
        );
        when(timeRecordRepository.findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(FROM, TO))
            .thenReturn(records);

        List<BenchmarkResponse> result = useCase.execute(FROM, TO);

        assertThat(result).hasSize(2);
        // Alice (0.5) deve vir antes de Bob (0.25) — ordem DESC
        assertThat(result.get(0).label()).isEqualTo("Alice");
        assertThat(result.get(1).label()).isEqualTo("Bob");
        assertThat(result.get(0).avgOee()).isGreaterThan(result.get(1).avgOee());
    }

    // ── Caso 2: Período > 90 dias → IllegalArgumentException ─────────────────

    @Test
    void shouldThrowIllegalArgument_whenPeriodExceeds90Days() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to   = from.plusDays(91); // 91 dias

        assertThatThrownBy(() -> useCase.execute(from, to))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("90");
    }

    // ── Caso 3: stdDev null quando sampleCount < 3 ───────────────────────────

    @Test
    void shouldReturnNullStdDev_whenSampleCountLessThan3() {
        // Worker com apenas 2 amostras (2 dias)
        List<TimeRecord> records = List.of(
            entry(1L, "Alice", FROM, ldt(8, 0)),
            process(1L, "Alice", FROM, bd("4.0")),
            exit(1L, "Alice", FROM, ldt(16, 0)),

            entry(1L, "Alice", FROM.plusDays(1), ldt(8, 0)),
            process(1L, "Alice", FROM.plusDays(1), bd("3.0")),
            exit(1L, "Alice", FROM.plusDays(1), ldt(16, 0))
        );
        when(timeRecordRepository.findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(FROM, TO))
            .thenReturn(records);

        List<BenchmarkResponse> result = useCase.execute(FROM, TO);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().sampleCount()).isEqualTo(2);
        assertThat(result.getFirst().stdDev()).isNull(); // < 3 amostras → stdDev null
    }

    // ── Caso 4: stdDev correto quando sampleCount >= 3 ───────────────────────

    @Test
    void shouldReturnNonNullStdDev_whenSampleCountAtLeast3() {
        // Worker com 3 amostras (3 dias)
        List<TimeRecord> records = List.of(
            entry(1L, "Alice", FROM, ldt(8, 0)),
            process(1L, "Alice", FROM, bd("4.0")),
            exit(1L, "Alice", FROM, ldt(16, 0)),

            entry(1L, "Alice", FROM.plusDays(1), ldt(8, 0)),
            process(1L, "Alice", FROM.plusDays(1), bd("2.0")),
            exit(1L, "Alice", FROM.plusDays(1), ldt(16, 0)),

            entry(1L, "Alice", FROM.plusDays(2), ldt(8, 0)),
            process(1L, "Alice", FROM.plusDays(2), bd("6.0")),
            exit(1L, "Alice", FROM.plusDays(2), ldt(16, 0))
        );
        when(timeRecordRepository.findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(FROM, TO))
            .thenReturn(records);

        List<BenchmarkResponse> result = useCase.execute(FROM, TO);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().sampleCount()).isEqualTo(3);
        assertThat(result.getFirst().stdDev()).isNotNull();
        assertThat(result.getFirst().stdDev()).isGreaterThan(0.0);
    }

    // ── Caso 5: Lista vazia quando sem registros no período ──────────────────

    @Test
    void shouldReturnEmptyList_whenNoRecordsInPeriod() {
        when(timeRecordRepository.findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(FROM, TO))
            .thenReturn(List.of());

        List<BenchmarkResponse> result = useCase.execute(FROM, TO);

        assertThat(result).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TimeRecord entry(Long workerId, String workerName, LocalDate date, LocalDateTime startTime) {
        return TimeRecord.builder()
            .workerId(workerId)
            .workerName(workerName)
            .profileDate(date)
            .recordType(RecordType.REGISTRO_ENTRADA)
            .startTime(startTime)
            .build();
    }

    private TimeRecord process(Long workerId, String workerName, LocalDate date, BigDecimal hours) {
        return TimeRecord.builder()
            .workerId(workerId)
            .workerName(workerName)
            .profileDate(date)
            .recordType(RecordType.PROCESSO)
            .hours(hours)
            .build();
    }

    private TimeRecord exit(Long workerId, String workerName, LocalDate date, LocalDateTime endTime) {
        return TimeRecord.builder()
            .workerId(workerId)
            .workerName(workerName)
            .profileDate(date)
            .recordType(RecordType.REGISTRO_SAIDA)
            .startTime(endTime)
            .endTime(endTime)
            .build();
    }

    private LocalDateTime ldt(int hour, int minute) {
        return LocalDateTime.of(2026, 4, 1, hour, minute);
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
