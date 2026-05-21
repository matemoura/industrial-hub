package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.common.kpi.application.OeeCalculator;
import com.industrialhub.backend.oee.application.dto.BenchmarkResponse;
import com.industrialhub.backend.oee.domain.TimeRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Componente auxiliar para calcular métricas de benchmark de OEE.
 * Encapsula o cálculo de OEE por dia/worker, desvio padrão e construção do BenchmarkResponse.
 */
@Component
public class BenchmarkCalculator {

    private final OeeCalculator oeeCalculator;

    public BenchmarkCalculator(OeeCalculator oeeCalculator) {
        this.oeeCalculator = oeeCalculator;
    }

    /**
     * Calcula o OEE diário para cada combinação de (worker, date) nos registros fornecidos.
     * Retorna a lista de valores de OEE (0.0 a 1.0).
     */
    public List<Double> computeDailyOees(List<TimeRecord> records) {
        // Agrupa por (workerId, profileDate) — cada chave representa um "dia de trabalho"
        Map<String, List<TimeRecord>> byWorkerDay = records.stream()
                .collect(Collectors.groupingBy(r -> r.getWorkerId() + "|" + r.getProfileDate()));

        return byWorkerDay.values().stream()
                .map(dayRecords -> oeeCalculator.computeAvg(dayRecords))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Constrói um BenchmarkResponse a partir de um label, lista de valores OEE e
     * contagem de registros sem turno (null quando não aplicável).
     */
    public BenchmarkResponse buildResponse(String label, List<Double> oeeValues, Long recordsWithoutShift) {
        if (oeeValues.isEmpty()) {
            return new BenchmarkResponse(label, 0.0, 0.0, 0.0, null, 0, recordsWithoutShift);
        }

        double avg = oeeValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double min = oeeValues.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = oeeValues.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        long count = oeeValues.size();
        Double stdDev = calculateStdDev(oeeValues);

        return new BenchmarkResponse(label, avg, min, max, stdDev, count, recordsWithoutShift);
    }

    /**
     * Valida que o período [from, to] é válido e não excede 90 dias.
     */
    public void validatePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Período inválido: 'from' deve ser anterior a 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) > 90) {
            throw new IllegalArgumentException("Period cannot exceed 90 days");
        }
    }

    /**
     * Calcula o desvio padrão populacional.
     * Retorna null se a amostra tiver menos de 3 valores (estatisticamente insignificante).
     */
    public Double calculateStdDev(List<Double> values) {
        if (values.size() < 3) {
            return null;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }
}
