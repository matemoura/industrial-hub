package com.industrialhub.backend.qms;

import com.industrialhub.backend.common.application.dto.TimeSeriesResponse;
import com.industrialhub.backend.qms.application.dto.NcParetoResponse;
import com.industrialhub.backend.qms.application.usecase.GetNcAnalyticsUseCase;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class GetNcAnalyticsUseCaseTest {

    @Mock private NonConformanceRepository repository;

    private GetNcAnalyticsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetNcAnalyticsUseCase(repository);
    }

    // --- Pareto tests ---

    @Test
    void pareto_shouldThrow_forInvalidDays() {
        assertThatThrownBy(() -> useCase.executePareto(45))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("days deve ser 30, 90 ou 180");
    }

    @Test
    void pareto_shouldReturnAllZeros_whenNoPeriodNCs() {
        when(repository.findAllCreatedAfter(any())).thenReturn(List.of());

        NcParetoResponse response = useCase.executePareto(30);

        assertThat(response.byType()).hasSize(NcType.values().length);
        assertThat(response.bySeverity()).hasSize(NcSeverity.values().length);
        assertThat(response.byType().values()).containsOnly(0L);
        assertThat(response.bySeverity().values()).containsOnly(0L);
    }

    @Test
    void pareto_shouldReturnCorrectCounts_withMixedNCs() {
        NonConformance nc1 = buildNc(NcType.PROCESS, NcSeverity.HIGH);
        NonConformance nc2 = buildNc(NcType.PROCESS, NcSeverity.CRITICAL);
        NonConformance nc3 = buildNc(NcType.PRODUCT, NcSeverity.HIGH);

        when(repository.findAllCreatedAfter(any())).thenReturn(List.of(nc1, nc2, nc3));

        NcParetoResponse response = useCase.executePareto(30);

        assertThat(response.byType().get("PROCESS")).isEqualTo(2L);
        assertThat(response.byType().get("PRODUCT")).isEqualTo(1L);
        assertThat(response.bySeverity().get("HIGH")).isEqualTo(2L);
        assertThat(response.bySeverity().get("CRITICAL")).isEqualTo(1L);
        assertThat(response.bySeverity().get("LOW")).isEqualTo(0L);
    }

    // --- Trend tests ---

    @Test
    void trend_shouldThrow_forInvalidWeeks() {
        assertThatThrownBy(() -> useCase.executeTrend(2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weeks deve ser entre 4 e 52");
    }

    @Test
    void trend_shouldReturnZeroCounts_forEmptyWeeks() {
        when(repository.findAllCreatedBetween(any(), any())).thenReturn(List.of());

        TimeSeriesResponse response = useCase.executeTrend(4);

        assertThat(response.labels()).hasSize(4);
        assertThat(response.values()).hasSize(4);
        assertThat(response.values()).containsOnly(0.0);
    }

    @Test
    void trend_shouldGroupNCs_byIsoWeek() {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.with(DayOfWeek.SUNDAY);
        // NC from 2 weeks ago
        LocalDate twoWeeksAgo = endDate.minusWeeks(2).with(DayOfWeek.WEDNESDAY);

        NonConformance nc1 = buildNcWithDate(NcType.PROCESS, NcSeverity.LOW, twoWeeksAgo.atTime(10, 0));
        NonConformance nc2 = buildNcWithDate(NcType.PRODUCT, NcSeverity.HIGH, twoWeeksAgo.atTime(14, 0));

        when(repository.findAllCreatedBetween(any(), any())).thenReturn(List.of(nc1, nc2));

        TimeSeriesResponse response = useCase.executeTrend(4);

        assertThat(response.labels()).hasSize(4);
        // At least one week should have count 2
        assertThat(response.values()).anyMatch(v -> v == 2.0);
        // NCs are never null in trend
        assertThat(response.values()).doesNotContainNull();
    }

    private NonConformance buildNc(NcType type, NcSeverity severity) {
        return buildNcWithDate(type, severity, LocalDateTime.now().minusDays(5));
    }

    private NonConformance buildNcWithDate(NcType type, NcSeverity severity, LocalDateTime reportedAt) {
        return NonConformance.builder()
                .id(UUID.randomUUID())
                .title("Test NC")
                .type(type)
                .severity(severity)
                .status(NcStatus.OPEN)
                .reportedBy("operator")
                .reportedAt(reportedAt)
                .build();
    }
}
