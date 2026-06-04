package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.CapaAgingResponse;
import com.industrialhub.backend.qms.application.dto.CapaAgingResponse.AgingBucket;
import com.industrialhub.backend.qms.application.dto.CapaAgingResponse.OverdueBySeverity;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import com.industrialhub.backend.qms.infrastructure.projection.CapaAgingProjection;
import com.industrialhub.backend.qms.infrastructure.projection.CapaResolutionProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sprint 39 / ADR-050 Decisão 4: calcula aging de CAPAs abertas.
 * Cálculo feito em Java (não em JPQL) para testabilidade e uso de LocalDate.now() como referência.
 */
@Service
@Transactional(readOnly = true)
public class GetCapaAgingUseCase {

    private final CorrectiveActionRepository correctiveActionRepository;

    public GetCapaAgingUseCase(CorrectiveActionRepository correctiveActionRepository) {
        this.correctiveActionRepository = correctiveActionRepository;
    }

    public CapaAgingResponse execute() {
        return execute(LocalDate.now());
    }

    /**
     * Overload com data injetável para facilitar testes unitários.
     */
    public CapaAgingResponse execute(LocalDate today) {
        List<CapaAgingProjection> open = correctiveActionRepository.findOpenCapasForAging();

        long totalOpen = open.size();

        long overdueCount = open.stream()
                .filter(a -> a.getDueDate() != null && a.getDueDate().isBefore(today))
                .count();

        long noDueDateCount = open.stream()
                .filter(a -> a.getDueDate() == null)
                .count();

        long b0to7   = countFutureInRange(open, today, 0, 7);
        long b8to15  = countFutureInRange(open, today, 8, 15);
        long b16to30 = countFutureInRange(open, today, 16, 30);
        long bOver30 = countFutureInRange(open, today, 31, Long.MAX_VALUE);

        List<OverdueBySeverity> bySeverity = open.stream()
                .filter(a -> a.getDueDate() != null && a.getDueDate().isBefore(today))
                .collect(Collectors.groupingBy(CapaAgingProjection::getNcSeverity, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new OverdueBySeverity(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(OverdueBySeverity::severity))
                .toList();

        double avgResolutionDaysOpen = calculateAvgResolutionDays();

        return new CapaAgingResponse(
                totalOpen,
                overdueCount,
                noDueDateCount,
                new AgingBucket(b0to7,   "0–7 dias"),
                new AgingBucket(b8to15,  "8–15 dias"),
                new AgingBucket(b16to30, "16–30 dias"),
                new AgingBucket(bOver30, ">30 dias"),
                bySeverity,
                avgResolutionDaysOpen
        );
    }

    /**
     * Calcula a média de dias entre createdAt e completedAt para CAPAs com status DONE.
     * Retorna 0.0 se não houver CAPAs concluídas (evita NaN/divisão por zero).
     */
    private double calculateAvgResolutionDays() {
        List<CapaResolutionProjection> resolved =
                correctiveActionRepository.findDoneCapasForResolutionMetric();

        if (resolved.isEmpty()) {
            return 0.0;
        }

        return resolved.stream()
                .mapToLong(r -> ChronoUnit.DAYS.between(r.getCreatedAt(), r.getCompletedAt().toLocalDate()))
                .average()
                .orElse(0.0);
    }

    private long countFutureInRange(List<CapaAgingProjection> list, LocalDate today,
                                    long minDays, long maxDays) {
        return list.stream()
                .filter(a -> a.getDueDate() != null && !a.getDueDate().isBefore(today))
                .filter(a -> {
                    long delta = ChronoUnit.DAYS.between(today, a.getDueDate());
                    return delta >= minDays && (maxDays == Long.MAX_VALUE || delta <= maxDays);
                })
                .count();
    }
}
