package com.industrialhub.backend.qms;

import com.industrialhub.backend.qms.application.dto.CapaAgingResponse;
import com.industrialhub.backend.qms.application.usecase.GetCapaAgingUseCase;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import com.industrialhub.backend.qms.infrastructure.projection.CapaAgingProjection;
import com.industrialhub.backend.qms.infrastructure.projection.CapaResolutionProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCapaAgingUseCaseTest {

    @Mock
    private CorrectiveActionRepository correctiveActionRepository;

    private GetCapaAgingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCapaAgingUseCase(correctiveActionRepository);
    }

    @Test
    void execute_emptyList_returnsZeroCounts() {
        when(correctiveActionRepository.findOpenCapasForAging()).thenReturn(List.of());
        when(correctiveActionRepository.findDoneCapasForResolutionMetric()).thenReturn(List.of());

        CapaAgingResponse response = useCase.execute(LocalDate.of(2026, 6, 5));

        assertThat(response.totalOpen()).isZero();
        assertThat(response.overdueCount()).isZero();
        assertThat(response.noDueDateCount()).isZero();
        assertThat(response.overdueByNcSeverity()).isEmpty();
        assertThat(response.avgResolutionDaysOpen()).isZero();
    }

    @Test
    void execute_overdueCapas_countedCorrectly() {
        LocalDate today = LocalDate.of(2026, 6, 5);

        List<CapaAgingProjection> projections = List.of(
                mockProjection(today.minusDays(5), "CRITICAL"),   // overdue
                mockProjection(today.minusDays(1), "HIGH"),       // overdue
                mockProjection(today.plusDays(3), "MEDIUM"),      // bucket 0-7
                mockProjection(today.plusDays(10), "LOW"),        // bucket 8-15
                mockProjection(null, "LOW")                       // no due date
        );

        when(correctiveActionRepository.findOpenCapasForAging()).thenReturn(projections);
        when(correctiveActionRepository.findDoneCapasForResolutionMetric()).thenReturn(List.of());

        CapaAgingResponse response = useCase.execute(today);

        assertThat(response.totalOpen()).isEqualTo(5);
        assertThat(response.overdueCount()).isEqualTo(2);
        assertThat(response.noDueDateCount()).isEqualTo(1);
        assertThat(response.bucket0to7().count()).isEqualTo(1);
        assertThat(response.bucket8to15().count()).isEqualTo(1);
        assertThat(response.bucket16to30().count()).isZero();
        assertThat(response.bucketOver30().count()).isZero();
        assertThat(response.overdueByNcSeverity()).hasSize(2);
    }

    @Test
    void execute_bucketDistribution_correctBuckets() {
        LocalDate today = LocalDate.of(2026, 6, 5);

        List<CapaAgingProjection> projections = List.of(
                mockProjection(today.plusDays(0), "MEDIUM"),   // bucket 0-7
                mockProjection(today.plusDays(7), "MEDIUM"),   // bucket 0-7
                mockProjection(today.plusDays(8), "HIGH"),     // bucket 8-15
                mockProjection(today.plusDays(15), "HIGH"),    // bucket 8-15
                mockProjection(today.plusDays(16), "LOW"),     // bucket 16-30
                mockProjection(today.plusDays(30), "LOW"),     // bucket 16-30
                mockProjection(today.plusDays(31), "CRITICAL"),// bucket >30
                mockProjection(today.plusDays(100), "CRITICAL")// bucket >30
        );

        when(correctiveActionRepository.findOpenCapasForAging()).thenReturn(projections);
        when(correctiveActionRepository.findDoneCapasForResolutionMetric()).thenReturn(List.of());

        CapaAgingResponse response = useCase.execute(today);

        assertThat(response.bucket0to7().count()).isEqualTo(2);
        assertThat(response.bucket8to15().count()).isEqualTo(2);
        assertThat(response.bucket16to30().count()).isEqualTo(2);
        assertThat(response.bucketOver30().count()).isEqualTo(2);
        assertThat(response.overdueCount()).isZero();
    }

    @Test
    void execute_overdueByNcSeverity_groupedCorrectly() {
        LocalDate today = LocalDate.of(2026, 6, 5);

        List<CapaAgingProjection> projections = List.of(
                mockProjection(today.minusDays(1), "CRITICAL"),
                mockProjection(today.minusDays(2), "CRITICAL"),
                mockProjection(today.minusDays(3), "HIGH"),
                mockProjection(today.plusDays(5), "MEDIUM")  // not overdue
        );

        when(correctiveActionRepository.findOpenCapasForAging()).thenReturn(projections);
        when(correctiveActionRepository.findDoneCapasForResolutionMetric()).thenReturn(List.of());

        CapaAgingResponse response = useCase.execute(today);

        assertThat(response.overdueByNcSeverity()).hasSize(2);
        assertThat(response.overdueByNcSeverity())
                .anySatisfy(item -> {
                    assertThat(item.severity()).isEqualTo("CRITICAL");
                    assertThat(item.overdueCount()).isEqualTo(2);
                });
        assertThat(response.overdueByNcSeverity())
                .anySatisfy(item -> {
                    assertThat(item.severity()).isEqualTo("HIGH");
                    assertThat(item.overdueCount()).isEqualTo(1);
                });
    }

    @Test
    void execute_bucketLabelsCorrect() {
        when(correctiveActionRepository.findOpenCapasForAging()).thenReturn(List.of());
        when(correctiveActionRepository.findDoneCapasForResolutionMetric()).thenReturn(List.of());

        CapaAgingResponse response = useCase.execute(LocalDate.now());

        assertThat(response.bucket0to7().label()).isEqualTo("0–7 dias");
        assertThat(response.bucket8to15().label()).isEqualTo("8–15 dias");
        assertThat(response.bucket16to30().label()).isEqualTo("16–30 dias");
        assertThat(response.bucketOver30().label()).isEqualTo(">30 dias");
    }

    @Test
    void execute_avgResolutionDaysOpen_noResolvedCapas_returnsZero() {
        when(correctiveActionRepository.findOpenCapasForAging()).thenReturn(List.of());
        when(correctiveActionRepository.findDoneCapasForResolutionMetric()).thenReturn(List.of());

        CapaAgingResponse response = useCase.execute(LocalDate.of(2026, 6, 5));

        assertThat(response.avgResolutionDaysOpen()).isZero();
    }

    @Test
    void execute_avgResolutionDaysOpen_calculatedFromDoneCapas() {
        LocalDate today = LocalDate.of(2026, 6, 5);
        when(correctiveActionRepository.findOpenCapasForAging()).thenReturn(List.of());

        // CAPA 1: created 2026-05-01, completed 2026-06-01 → 31 days
        // CAPA 2: created 2026-05-06, completed 2026-06-05 → 30 days
        // avg = 30.5
        List<CapaResolutionProjection> resolved = List.of(
                mockResolution(LocalDate.of(2026, 5, 1), LocalDateTime.of(2026, 6, 1, 10, 0)),
                mockResolution(LocalDate.of(2026, 5, 6), LocalDateTime.of(2026, 6, 5, 9, 0))
        );
        when(correctiveActionRepository.findDoneCapasForResolutionMetric()).thenReturn(resolved);

        CapaAgingResponse response = useCase.execute(today);

        assertThat(response.avgResolutionDaysOpen()).isEqualTo(30.5, within(0.01));
    }

    private CapaResolutionProjection mockResolution(LocalDate createdAt, LocalDateTime completedAt) {
        return new CapaResolutionProjection() {
            public LocalDate getCreatedAt() { return createdAt; }
            public LocalDateTime getCompletedAt() { return completedAt; }
        };
    }

    private CapaAgingProjection mockProjection(LocalDate dueDate, String severity) {
        return new CapaAgingProjection() {
            public UUID getActionId() { return UUID.randomUUID(); }
            public LocalDate getDueDate() { return dueDate; }
            public String getStatus() { return "PENDING"; }
            public String getNcSeverity() { return severity; }
        };
    }
}
