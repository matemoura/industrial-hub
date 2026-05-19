package com.industrialhub.backend.common.alertthreshold;

import com.industrialhub.backend.common.application.usecase.AlertEvaluatorUseCase;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.domain.*;
import com.industrialhub.backend.common.infrastructure.AlertThresholdRepository;
import com.industrialhub.backend.common.infrastructure.NotificationRepository;
import com.industrialhub.backend.common.kpi.application.OeeCalculator;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertEvaluatorUseCaseTest {

    @Mock
    private AlertThresholdRepository alertThresholdRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TimeRecordRepository timeRecordRepository;
    @Mock
    private NonConformanceRepository nonConformanceRepository;
    @Mock
    private WorkOrderRepository workOrderRepository;
    @Mock
    private OeeCalculator oeeCalculator;

    private AlertEvaluatorUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AlertEvaluatorUseCase(
                alertThresholdRepository,
                notificationRepository,
                userRepository,
                timeRecordRepository,
                nonConformanceRepository,
                workOrderRepository,
                oeeCalculator
        );
    }

    @Test
    void shouldFireAlert_whenOeeAvgBelowThreshold() {
        AlertThreshold threshold = AlertThreshold.builder()
                .id(UUID.randomUUID())
                .metric(AlertMetric.OEE_AVG_BELOW)
                .threshold(0.65)
                .emailEnabled(false)
                .active(true)
                .build();

        when(alertThresholdRepository.findAllByActiveTrueOrderByMetric()).thenReturn(List.of(threshold));
        when(notificationRepository.existsByMetricAndCreatedAtAfter(eq("OEE_AVG_BELOW"), any())).thenReturn(false);
        when(timeRecordRepository.findByPeriodAndOptionalWorker(any(LocalDate.class), any(LocalDate.class), isNull()))
                .thenReturn(List.of());
        when(oeeCalculator.computeAvg(anyList())).thenReturn(0.55); // abaixo do threshold 0.65

        Notification saved = Notification.builder()
                .id(UUID.randomUUID())
                .title("Alerta: OEE médio abaixo do limite")
                .body("Corpo")
                .severity(NotificationSeverity.CRITICAL)
                .metric("OEE_AVG_BELOW")
                .createdAt(LocalDateTime.now())
                .build();
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        int result = useCase.execute();

        assertThat(result).isEqualTo(1);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMetric()).isEqualTo("OEE_AVG_BELOW");
        assertThat(captor.getValue().getSeverity()).isEqualTo(NotificationSeverity.CRITICAL);
        assertThat(captor.getValue().getUsername()).isNull(); // broadcast
    }

    @Test
    void shouldNotFireAlert_whenOeeAboveThreshold() {
        AlertThreshold threshold = AlertThreshold.builder()
                .id(UUID.randomUUID())
                .metric(AlertMetric.OEE_AVG_BELOW)
                .threshold(0.65)
                .emailEnabled(false)
                .active(true)
                .build();

        when(alertThresholdRepository.findAllByActiveTrueOrderByMetric()).thenReturn(List.of(threshold));
        when(notificationRepository.existsByMetricAndCreatedAtAfter(eq("OEE_AVG_BELOW"), any())).thenReturn(false);
        when(timeRecordRepository.findByPeriodAndOptionalWorker(any(LocalDate.class), any(LocalDate.class), isNull()))
                .thenReturn(List.of());
        when(oeeCalculator.computeAvg(anyList())).thenReturn(0.80); // acima do threshold

        int result = useCase.execute();

        assertThat(result).isEqualTo(0);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void shouldNotFireAlert_whenDebounceActive() {
        AlertThreshold threshold = AlertThreshold.builder()
                .id(UUID.randomUUID())
                .metric(AlertMetric.NC_CRITICAL_ABOVE)
                .threshold(3.0)
                .emailEnabled(false)
                .active(true)
                .build();

        when(alertThresholdRepository.findAllByActiveTrueOrderByMetric()).thenReturn(List.of(threshold));
        // Debounce ativo: já existe notificação nos últimos 60 minutos
        when(notificationRepository.existsByMetricAndCreatedAtAfter(eq("NC_CRITICAL_ABOVE"), any())).thenReturn(true);

        int result = useCase.execute();

        assertThat(result).isEqualTo(0);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void shouldNotSendEmail_whenEmailDisabled() {
        AlertThreshold threshold = AlertThreshold.builder()
                .id(UUID.randomUUID())
                .metric(AlertMetric.NC_CRITICAL_ABOVE)
                .threshold(3.0)
                .emailEnabled(false) // email desabilitado
                .active(true)
                .build();

        when(alertThresholdRepository.findAllByActiveTrueOrderByMetric()).thenReturn(List.of(threshold));
        when(notificationRepository.existsByMetricAndCreatedAtAfter(any(), any())).thenReturn(false);
        when(nonConformanceRepository.countBySeverityAndStatus(NcSeverity.CRITICAL, NcStatus.OPEN)).thenReturn(5L);
        when(nonConformanceRepository.countBySeverityAndStatus(NcSeverity.CRITICAL, NcStatus.IN_ANALYSIS)).thenReturn(0L);
        when(notificationRepository.save(any())).thenReturn(Notification.builder()
                .id(UUID.randomUUID()).title("t").body("b").severity(NotificationSeverity.WARNING)
                .metric("NC_CRITICAL_ABOVE").createdAt(LocalDateTime.now()).build());

        int result = useCase.execute();

        assertThat(result).isEqualTo(1);
        // Não há interação com userRepository pois email está desabilitado
        verify(userRepository, never()).findByRoleIn(any());
    }

    @Test
    void shouldContinueProcessing_whenOneThresholdFails() {
        AlertThreshold badThreshold = AlertThreshold.builder()
                .id(UUID.randomUUID())
                .metric(AlertMetric.OEE_AVG_BELOW)
                .threshold(0.65)
                .emailEnabled(false)
                .active(true)
                .build();

        AlertThreshold goodThreshold = AlertThreshold.builder()
                .id(UUID.randomUUID())
                .metric(AlertMetric.NC_CRITICAL_ABOVE)
                .threshold(3.0)
                .emailEnabled(false)
                .active(true)
                .build();

        when(alertThresholdRepository.findAllByActiveTrueOrderByMetric())
                .thenReturn(List.of(badThreshold, goodThreshold));
        when(notificationRepository.existsByMetricAndCreatedAtAfter(any(), any())).thenReturn(false);

        // Primeiro threshold lança exceção
        when(timeRecordRepository.findByPeriodAndOptionalWorker(any(), any(), any()))
                .thenThrow(new RuntimeException("Erro simulado no TimeRecordRepository"));

        // Segundo threshold funciona normalmente — OEE não chega a ser avaliado novamente
        when(nonConformanceRepository.countBySeverityAndStatus(NcSeverity.CRITICAL, NcStatus.OPEN)).thenReturn(5L);
        when(nonConformanceRepository.countBySeverityAndStatus(NcSeverity.CRITICAL, NcStatus.IN_ANALYSIS)).thenReturn(0L);
        when(notificationRepository.save(any())).thenReturn(Notification.builder()
                .id(UUID.randomUUID()).title("t").body("b").severity(NotificationSeverity.WARNING)
                .metric("NC_CRITICAL_ABOVE").createdAt(LocalDateTime.now()).build());

        int result = useCase.execute();

        // O segundo threshold deve ser avaliado mesmo após o primeiro falhar
        assertThat(result).isEqualTo(1);
    }
}
