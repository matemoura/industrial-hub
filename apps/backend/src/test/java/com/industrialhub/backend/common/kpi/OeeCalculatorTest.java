package com.industrialhub.backend.common.kpi;

import com.industrialhub.backend.common.kpi.application.OeeCalculator;
import com.industrialhub.backend.oee.domain.ImportBatch;
import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.domain.TimeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class OeeCalculatorTest {

    private OeeCalculator calculator;

    private static final LocalDate DATE = LocalDate.of(2026, 5, 20);
    private ImportBatch batch;

    @BeforeEach
    void setUp() {
        calculator = new OeeCalculator();
        batch = ImportBatch.builder().id(UUID.randomUUID()).build();
    }

    // === Caso 1: excludePlannedDowntime=false — sem alteração no valor ===
    @Test
    void computeAvg_withoutPlannedDowntime_returnsStandardAvailability() {
        // Turno 8h–16h (8h = 480 min), produtivas 4h
        List<TimeRecord> records = List.of(
                entry(DATE, 8, 0),
                process(DATE, new BigDecimal("4.0")),
                exit(DATE, 16, 0)
        );

        Double result = calculator.computeAvg(records); // sem mapa de paradas

        // Disponibilidade = 4 / 8 = 0.5
        assertThat(result).isNotNull();
        assertThat(result).isCloseTo(0.5, within(0.001));
    }

    // === Caso 2: excludePlannedDowntime=true com parada de planta inteira — denominator reduzido ===
    @Test
    void computeAvg_withPlannedDowntime_reducesEffectiveDenominator() {
        // Turno 8h–16h (480 min), produtivas 4h
        // Parada planejada: 120 min → denominador efetivo = 360 min = 6h
        // Disponibilidade esperada = 4 / 6 ≈ 0.6667
        List<TimeRecord> records = List.of(
                entry(DATE, 8, 0),
                process(DATE, new BigDecimal("4.0")),
                exit(DATE, 16, 0)
        );

        Map<LocalDate, Integer> plannedMinutesByDate = Map.of(DATE, 120);

        Double result = calculator.computeAvg(records, plannedMinutesByDate);

        assertThat(result).isNotNull();
        assertThat(result).isCloseTo(0.6667, within(0.001));
    }

    // === Caso 3: cap de 1.0 quando paradas excedem turno ===
    @Test
    void computeAvg_whenPlannedDowntimeExceedsTotalTime_usesOriginalDenominator() {
        // Turno 8h–10h (120 min), produtivas 2h
        // Parada planejada: 200 min (> turno) → fallback para denominador original
        // Disponibilidade = 2 / 2 = 1.0
        List<TimeRecord> records = List.of(
                entry(DATE, 8, 0),
                process(DATE, new BigDecimal("2.0")),
                exit(DATE, 10, 0)
        );

        Map<LocalDate, Integer> plannedMinutesByDate = Map.of(DATE, 200);

        Double result = calculator.computeAvg(records, plannedMinutesByDate);

        // Com fallback, denominator = 120 min = 2h → 2/2 = 1.0 (cap)
        assertThat(result).isNotNull();
        assertThat(result).isLessThanOrEqualTo(1.0);
        assertThat(result).isCloseTo(1.0, within(0.001));
    }

    // === Caso 4: sem data no mapa → mesmo resultado que sem paradas ===
    @Test
    void computeAvg_withEmptyPlannedMinutesMap_returnsSameAsWithout() {
        List<TimeRecord> records = List.of(
                entry(DATE, 8, 0),
                process(DATE, new BigDecimal("4.0")),
                exit(DATE, 16, 0)
        );

        Double withoutMap = calculator.computeAvg(records);
        Double withEmptyMap = calculator.computeAvg(records, Map.of());

        assertThat(withEmptyMap).isEqualTo(withoutMap);
    }

    // === Caso 5: cap de 1.0 mesmo sem paradas (produtivas > turno) ===
    @Test
    void computeAvg_withExcessProductive_capAt1() {
        // Produtivas 10h, turno 8h → sem cap seria 1.25, com cap deve ser 1.0
        List<TimeRecord> records = List.of(
                entry(DATE, 8, 0),
                process(DATE, new BigDecimal("10.0")),
                exit(DATE, 16, 0)
        );

        Double result = calculator.computeAvg(records);

        assertThat(result).isNotNull();
        assertThat(result).isLessThanOrEqualTo(1.0);
    }

    // --- helpers ---

    private TimeRecord entry(LocalDate date, int hour, int min) {
        return TimeRecord.builder()
                .batch(batch)
                .workerId(1L)
                .workerName("Worker")
                .profileDate(date)
                .recordType(RecordType.REGISTRO_ENTRADA)
                .startTime(LocalDateTime.of(date, java.time.LocalTime.of(hour, min)))
                .endTime(LocalDateTime.of(date, java.time.LocalTime.of(hour, min)))
                .build();
    }

    private TimeRecord exit(LocalDate date, int hour, int min) {
        return TimeRecord.builder()
                .batch(batch)
                .workerId(1L)
                .workerName("Worker")
                .profileDate(date)
                .recordType(RecordType.REGISTRO_SAIDA)
                .startTime(LocalDateTime.of(date, java.time.LocalTime.of(hour, min)))
                .endTime(LocalDateTime.of(date, java.time.LocalTime.of(hour, min)))
                .build();
    }

    private TimeRecord process(LocalDate date, BigDecimal hours) {
        return TimeRecord.builder()
                .batch(batch)
                .workerId(1L)
                .workerName("Worker")
                .profileDate(date)
                .recordType(RecordType.PROCESSO)
                .hours(hours)
                .build();
    }
}
