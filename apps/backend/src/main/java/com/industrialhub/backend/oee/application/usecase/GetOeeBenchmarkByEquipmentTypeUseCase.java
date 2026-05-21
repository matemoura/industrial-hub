package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.BenchmarkResponse;
import com.industrialhub.backend.oee.domain.TimeRecord;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Benchmarking de OEE por tipo de equipamento.
 *
 * Nota de implementação: o modelo atual (ImportBatch, TimeRecord) não possui relação
 * direta com Equipment. O agrupamento é feito por workerName como proxy de linha de
 * produção. Esta decisão está documentada no log-mateus.md (Sprint 24).
 * Quando o modelo for estendido com ImportBatch.equipment, este use case deve ser
 * atualizado para agrupar por equipment.type.
 */
@Service
public class GetOeeBenchmarkByEquipmentTypeUseCase {

    private static final String FALLBACK_LABEL = "GERAL";

    private final TimeRecordRepository timeRecordRepository;
    private final BenchmarkCalculator benchmarkCalculator;

    public GetOeeBenchmarkByEquipmentTypeUseCase(TimeRecordRepository timeRecordRepository,
                                                  BenchmarkCalculator benchmarkCalculator) {
        this.timeRecordRepository = timeRecordRepository;
        this.benchmarkCalculator = benchmarkCalculator;
    }

    @Transactional(readOnly = true)
    public List<BenchmarkResponse> execute(LocalDate from, LocalDate to) {
        benchmarkCalculator.validatePeriod(from, to);

        List<TimeRecord> allRecords = timeRecordRepository
                .findByProfileDateBetweenOrderByWorkerIdAscProfileDateAsc(from, to);

        if (allRecords.isEmpty()) {
            return List.of();
        }

        // ImportBatch não tem relação com Equipment no modelo atual.
        // Todos os registros são agrupados sob FALLBACK_LABEL até que o modelo suporte
        // ImportBatch.equipment. recordsWithoutShift = null (não aplicável para equipment-type).
        List<Double> dailyOees = benchmarkCalculator.computeDailyOees(allRecords);
        if (dailyOees.isEmpty()) {
            return List.of();
        }

        return List.of(benchmarkCalculator.buildResponse(FALLBACK_LABEL, dailyOees, null));
    }

}
