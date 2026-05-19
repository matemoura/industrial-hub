package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.auth.domain.Role;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertEvaluatorUseCase {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluatorUseCase.class);

    private final AlertThresholdRepository alertThresholdRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TimeRecordRepository timeRecordRepository;
    private final NonConformanceRepository nonConformanceRepository;
    private final WorkOrderRepository workOrderRepository;
    private final OeeCalculator oeeCalculator;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@industrialhub.com}")
    private String fromAddress;

    @Value("${mail.enabled:false}")
    private boolean mailEnabled;

    public AlertEvaluatorUseCase(AlertThresholdRepository alertThresholdRepository,
                                  NotificationRepository notificationRepository,
                                  UserRepository userRepository,
                                  TimeRecordRepository timeRecordRepository,
                                  NonConformanceRepository nonConformanceRepository,
                                  WorkOrderRepository workOrderRepository,
                                  OeeCalculator oeeCalculator) {
        this.alertThresholdRepository = alertThresholdRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.timeRecordRepository = timeRecordRepository;
        this.nonConformanceRepository = nonConformanceRepository;
        this.workOrderRepository = workOrderRepository;
        this.oeeCalculator = oeeCalculator;
    }

    @Transactional
    public int execute() {
        List<AlertThreshold> thresholds = alertThresholdRepository.findAllByActiveTrueOrderByMetric();
        int evaluated = 0;

        for (AlertThreshold threshold : thresholds) {
            try {
                boolean fired = evaluateThreshold(threshold);
                if (fired) evaluated++;
            } catch (Exception e) {
                log.error("Erro ao avaliar threshold {}: {}", threshold.getMetric(), e.getMessage(), e);
            }
        }

        return evaluated;
    }

    private boolean evaluateThreshold(AlertThreshold threshold) {
        String metricName = threshold.getMetric().name();

        // Debounce: não disparar se já existe notificação nos últimos 60 minutos
        if (notificationRepository.existsByMetricAndCreatedAtAfter(metricName,
                LocalDateTime.now().minusMinutes(60))) {
            log.debug("Debounce ativo para métrica {}", metricName);
            return false;
        }

        boolean conditionMet = switch (threshold.getMetric()) {
            case OEE_AVG_BELOW -> evaluateOeeAvgBelow(threshold.getThreshold());
            case NC_CRITICAL_ABOVE -> evaluateNcCriticalAbove(threshold.getThreshold());
            case WO_URGENT_PENDING_HOURS -> evaluateWoUrgentPendingHours(threshold.getThreshold());
        };

        if (!conditionMet) {
            return false;
        }

        // Criar notificação broadcast
        Notification notification = buildNotification(threshold);
        notificationRepository.save(notification);

        // Enviar email se habilitado
        if (threshold.isEmailEnabled()) {
            sendEmailAsync(threshold, notification);
        }

        return true;
    }

    private boolean evaluateOeeAvgBelow(double threshold) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(29);
        var records = timeRecordRepository.findByPeriodAndOptionalWorker(start, end, null);
        Double oeeAvg = oeeCalculator.computeAvg(records);
        return oeeAvg != null && oeeAvg < threshold;
    }

    private boolean evaluateNcCriticalAbove(double threshold) {
        long criticalOpen = nonConformanceRepository.countBySeverityAndStatus(
                NcSeverity.CRITICAL, NcStatus.OPEN) +
                nonConformanceRepository.countBySeverityAndStatus(
                        NcSeverity.CRITICAL, NcStatus.IN_ANALYSIS);
        return criticalOpen > threshold;
    }

    private boolean evaluateWoUrgentPendingHours(double thresholdHours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours((long) thresholdHours);
        long urgentOpen = workOrderRepository.countUrgentOpenOlderThan(
                WorkOrderPriority.URGENT,
                List.of(WorkOrderStatus.OPEN),
                cutoff);
        return urgentOpen > 0;
    }

    private Notification buildNotification(AlertThreshold threshold) {
        record AlertContent(String title, String body, NotificationSeverity severity) {}

        AlertContent content = switch (threshold.getMetric()) {
            case OEE_AVG_BELOW -> new AlertContent(
                "Alerta: OEE médio abaixo do limite",
                String.format("O OEE médio dos últimos 30 dias está abaixo de %.0f%%.", threshold.getThreshold() * 100),
                NotificationSeverity.CRITICAL
            );
            case NC_CRITICAL_ABOVE -> new AlertContent(
                "Alerta: Número de NCs críticas acima do limite",
                String.format("Existem mais de %.0f não-conformidades críticas abertas.", threshold.getThreshold()),
                NotificationSeverity.WARNING
            );
            case WO_URGENT_PENDING_HOURS -> new AlertContent(
                "Alerta: OSs urgentes pendentes há muito tempo",
                String.format("Existem ordens de serviço urgentes abertas há mais de %.0f horas.", threshold.getThreshold()),
                NotificationSeverity.WARNING
            );
        };

        return Notification.builder()
                .username(null)
                .title(content.title())
                .body(content.body())
                .severity(content.severity())
                .metric(threshold.getMetric().name())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Async
    public void sendEmailAsync(AlertThreshold threshold, Notification notification) {
        if (!mailEnabled || mailSender == null) {
            log.debug("Email desabilitado — notificação de alerta não enviada por email.");
            return;
        }
        try {
            List<String> emails = userRepository
                    .findByRoleIn(List.of(Role.SUPERVISOR, Role.ADMIN))
                    .stream()
                    .filter(u -> u.isActive() && u.getEmail() != null && !u.getEmail().isBlank())
                    .map(u -> u.getEmail())
                    .toList();

            for (String email : emails) {
                try {
                    SimpleMailMessage msg = new SimpleMailMessage();
                    msg.setFrom(fromAddress);
                    msg.setTo(email);
                    msg.setSubject("[Industrial Hub] " + notification.getTitle());
                    msg.setText(notification.getBody());
                    mailSender.send(msg);
                } catch (Exception e) {
                    log.error("Falha ao enviar email de alerta para {}: {}", email, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Erro ao enviar emails de alerta para métrica {}: {}",
                    threshold.getMetric(), e.getMessage());
        }
    }
}
