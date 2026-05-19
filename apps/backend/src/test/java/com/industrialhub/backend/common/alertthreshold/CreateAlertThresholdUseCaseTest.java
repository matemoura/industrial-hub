package com.industrialhub.backend.common.alertthreshold;

import com.industrialhub.backend.common.application.dto.AlertThresholdResponse;
import com.industrialhub.backend.common.application.dto.CreateAlertThresholdRequest;
import com.industrialhub.backend.common.application.usecase.CreateAlertThresholdUseCase;
import com.industrialhub.backend.common.domain.AlertMetric;
import com.industrialhub.backend.common.domain.AlertThreshold;
import com.industrialhub.backend.common.infrastructure.AlertThresholdRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateAlertThresholdUseCaseTest {

    @Mock
    private AlertThresholdRepository alertThresholdRepository;

    private CreateAlertThresholdUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateAlertThresholdUseCase(alertThresholdRepository);
    }

    @Test
    void shouldCreateThreshold_successfully() {
        CreateAlertThresholdRequest request = new CreateAlertThresholdRequest(
                AlertMetric.OEE_AVG_BELOW, 0.65, false
        );

        AlertThreshold saved = AlertThreshold.builder()
                .id(UUID.randomUUID())
                .metric(AlertMetric.OEE_AVG_BELOW)
                .threshold(0.65)
                .emailEnabled(false)
                .active(true)
                .createdBy("admin")
                .updatedAt(LocalDateTime.now())
                .build();

        when(alertThresholdRepository.findByMetricAndActiveTrue(AlertMetric.OEE_AVG_BELOW))
                .thenReturn(Optional.empty());
        when(alertThresholdRepository.save(any(AlertThreshold.class))).thenReturn(saved);

        AlertThresholdResponse response = useCase.execute(request, "admin");

        assertThat(response.metric()).isEqualTo(AlertMetric.OEE_AVG_BELOW);
        assertThat(response.threshold()).isEqualTo(0.65);
        assertThat(response.emailEnabled()).isFalse();
        assertThat(response.active()).isTrue();

        verify(alertThresholdRepository).save(any(AlertThreshold.class));
    }

    @Test
    void shouldThrow409_whenDuplicateActiveMetric() {
        CreateAlertThresholdRequest request = new CreateAlertThresholdRequest(
                AlertMetric.NC_CRITICAL_ABOVE, 3.0, false
        );

        AlertThreshold existing = AlertThreshold.builder()
                .id(UUID.randomUUID())
                .metric(AlertMetric.NC_CRITICAL_ABOVE)
                .threshold(3.0)
                .active(true)
                .build();

        when(alertThresholdRepository.findByMetricAndActiveTrue(AlertMetric.NC_CRITICAL_ABOVE))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(request, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NC_CRITICAL_ABOVE");

        verify(alertThresholdRepository, never()).save(any());
    }

    @Test
    void shouldCreateThreshold_withEmailEnabled() {
        CreateAlertThresholdRequest request = new CreateAlertThresholdRequest(
                AlertMetric.WO_URGENT_PENDING_HOURS, 4.0, true
        );

        AlertThreshold saved = AlertThreshold.builder()
                .id(UUID.randomUUID())
                .metric(AlertMetric.WO_URGENT_PENDING_HOURS)
                .threshold(4.0)
                .emailEnabled(true)
                .active(true)
                .createdBy("admin")
                .updatedAt(LocalDateTime.now())
                .build();

        when(alertThresholdRepository.findByMetricAndActiveTrue(AlertMetric.WO_URGENT_PENDING_HOURS))
                .thenReturn(Optional.empty());
        when(alertThresholdRepository.save(any(AlertThreshold.class))).thenReturn(saved);

        AlertThresholdResponse response = useCase.execute(request, "admin");

        assertThat(response.emailEnabled()).isTrue();
        assertThat(response.metric()).isEqualTo(AlertMetric.WO_URGENT_PENDING_HOURS);
    }
}
