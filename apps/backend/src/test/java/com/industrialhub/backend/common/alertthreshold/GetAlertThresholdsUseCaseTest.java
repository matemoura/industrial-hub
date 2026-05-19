package com.industrialhub.backend.common.alertthreshold;

import com.industrialhub.backend.common.application.dto.AlertThresholdResponse;
import com.industrialhub.backend.common.application.usecase.GetAlertThresholdsUseCase;
import com.industrialhub.backend.common.domain.AlertMetric;
import com.industrialhub.backend.common.domain.AlertThreshold;
import com.industrialhub.backend.common.infrastructure.AlertThresholdRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAlertThresholdsUseCaseTest {

    @Mock
    private AlertThresholdRepository alertThresholdRepository;

    private GetAlertThresholdsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetAlertThresholdsUseCase(alertThresholdRepository);
    }

    @Test
    void shouldReturnEmptyList_whenNoActiveThresholds() {
        when(alertThresholdRepository.findAllByActiveTrueOrderByMetric()).thenReturn(List.of());

        List<AlertThresholdResponse> result = useCase.execute();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnActiveThresholds_orderedByMetric() {
        List<AlertThreshold> thresholds = List.of(
                AlertThreshold.builder()
                        .id(UUID.randomUUID())
                        .metric(AlertMetric.NC_CRITICAL_ABOVE)
                        .threshold(3.0)
                        .emailEnabled(false)
                        .active(true)
                        .updatedAt(LocalDateTime.now())
                        .build(),
                AlertThreshold.builder()
                        .id(UUID.randomUUID())
                        .metric(AlertMetric.OEE_AVG_BELOW)
                        .threshold(0.65)
                        .emailEnabled(true)
                        .active(true)
                        .updatedAt(LocalDateTime.now())
                        .build()
        );

        when(alertThresholdRepository.findAllByActiveTrueOrderByMetric()).thenReturn(thresholds);

        List<AlertThresholdResponse> result = useCase.execute();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).metric()).isEqualTo(AlertMetric.NC_CRITICAL_ABOVE);
        assertThat(result.get(1).metric()).isEqualTo(AlertMetric.OEE_AVG_BELOW);
        assertThat(result.get(1).emailEnabled()).isTrue();
    }
}
