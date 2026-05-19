package com.industrialhub.backend.common.alertthreshold;

import com.industrialhub.backend.common.domain.AlertThresholdNotFoundException;
import com.industrialhub.backend.common.application.usecase.DeleteAlertThresholdUseCase;
import com.industrialhub.backend.common.domain.AlertMetric;
import com.industrialhub.backend.common.domain.AlertThreshold;
import com.industrialhub.backend.common.infrastructure.AlertThresholdRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteAlertThresholdUseCaseTest {

    @Mock
    private AlertThresholdRepository alertThresholdRepository;

    private DeleteAlertThresholdUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeleteAlertThresholdUseCase(alertThresholdRepository);
    }

    @Test
    void shouldSoftDelete_whenThresholdExists() {
        UUID id = UUID.randomUUID();
        AlertThreshold existing = AlertThreshold.builder()
                .id(id)
                .metric(AlertMetric.OEE_AVG_BELOW)
                .threshold(0.65)
                .emailEnabled(false)
                .active(true)
                .build();

        when(alertThresholdRepository.findById(id)).thenReturn(Optional.of(existing));
        when(alertThresholdRepository.save(any(AlertThreshold.class))).thenReturn(existing);

        useCase.execute(id);

        ArgumentCaptor<AlertThreshold> captor = ArgumentCaptor.forClass(AlertThreshold.class);
        verify(alertThresholdRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void shouldThrow404_whenThresholdNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(alertThresholdRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(unknownId))
                .isInstanceOf(AlertThresholdNotFoundException.class);

        verify(alertThresholdRepository, never()).save(any());
    }
}
