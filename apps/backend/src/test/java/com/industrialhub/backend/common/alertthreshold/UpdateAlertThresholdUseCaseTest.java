package com.industrialhub.backend.common.alertthreshold;

import com.industrialhub.backend.common.application.dto.AlertThresholdResponse;
import com.industrialhub.backend.common.application.dto.UpdateAlertThresholdRequest;
import com.industrialhub.backend.common.domain.AlertThresholdNotFoundException;
import com.industrialhub.backend.common.application.usecase.UpdateAlertThresholdUseCase;
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
class UpdateAlertThresholdUseCaseTest {

    @Mock
    private AlertThresholdRepository alertThresholdRepository;

    private UpdateAlertThresholdUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateAlertThresholdUseCase(alertThresholdRepository);
    }

    @Test
    void shouldUpdateThreshold_successfully() {
        UUID id = UUID.randomUUID();
        AlertThreshold existing = AlertThreshold.builder()
                .id(id)
                .metric(AlertMetric.OEE_AVG_BELOW)
                .threshold(0.65)
                .emailEnabled(false)
                .active(true)
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        UpdateAlertThresholdRequest request = new UpdateAlertThresholdRequest(0.70, true);

        AlertThreshold saved = AlertThreshold.builder()
                .id(id)
                .metric(AlertMetric.OEE_AVG_BELOW)
                .threshold(0.70)
                .emailEnabled(true)
                .active(true)
                .updatedAt(LocalDateTime.now())
                .build();

        when(alertThresholdRepository.findById(id)).thenReturn(Optional.of(existing));
        when(alertThresholdRepository.save(any(AlertThreshold.class))).thenReturn(saved);

        AlertThresholdResponse response = useCase.execute(id, request);

        assertThat(response.threshold()).isEqualTo(0.70);
        assertThat(response.emailEnabled()).isTrue();
        // metric deve permanecer imutável
        assertThat(response.metric()).isEqualTo(AlertMetric.OEE_AVG_BELOW);

        verify(alertThresholdRepository).save(any(AlertThreshold.class));
    }

    @Test
    void shouldThrow404_whenThresholdNotFound() {
        UUID unknownId = UUID.randomUUID();
        UpdateAlertThresholdRequest request = new UpdateAlertThresholdRequest(0.70, false);

        when(alertThresholdRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(unknownId, request))
                .isInstanceOf(AlertThresholdNotFoundException.class);

        verify(alertThresholdRepository, never()).save(any());
    }
}
