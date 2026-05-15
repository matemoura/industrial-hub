package com.industrialhub.backend.common.kpi.application;

import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

@Service
public class WeeklyReportService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportService.class);

    @Autowired(required = false) // null when mail.enabled=false (dev/test)
    private JavaMailSender mailSender;

    private final UserRepository userRepository;
    private final TimeRecordRepository timeRecordRepository;
    private final NonConformanceRepository nonConformanceRepository;
    private final WorkOrderRepository workOrderRepository;
    private final OeeCalculator oeeCalculator;

    @Value("${mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${spring.mail.username:noreply@industrialhub.com}")
    private String fromAddress;

    public WeeklyReportService(UserRepository userRepository,
                                TimeRecordRepository timeRecordRepository,
                                NonConformanceRepository nonConformanceRepository,
                                WorkOrderRepository workOrderRepository,
                                OeeCalculator oeeCalculator) {
        this.userRepository = userRepository;
        this.timeRecordRepository = timeRecordRepository;
        this.nonConformanceRepository = nonConformanceRepository;
        this.workOrderRepository = workOrderRepository;
        this.oeeCalculator = oeeCalculator;
    }

    @Async
    @Transactional(readOnly = true)
    public void send() {
        ReportData data = collectData();
        String body = buildBody(data);
        String subject = "[MSB] Relatório Semanal — " +
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        if (!mailEnabled || mailSender == null) {
            log.info("[MAIL DISABLED] Relatório semanal:\n{}", body);
            return;
        }

        List<String> emails = userRepository.findByRoleIn(List.of(Role.SUPERVISOR, Role.ADMIN)).stream()
                .map(User::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .toList();

        emails.forEach(email -> {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromAddress);
                msg.setTo(email);
                msg.setSubject(subject);
                msg.setText(body);
                mailSender.send(msg);
            } catch (Exception e) {
                log.error("Falha ao enviar relatório semanal para {}: {}", email, e.getMessage());
            }
        });
    }

    ReportData collectData() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);
        LocalDateTime weekStartDt = weekStart.atStartOfDay();
        LocalDateTime todayDt = today.plusDays(1).atStartOfDay();

        Double oeeAvg = oeeCalculator.computeAvg(
                timeRecordRepository.findByPeriodAndOptionalWorker(weekStart, today, null));
        long newNcs = nonConformanceRepository.countInPeriod(weekStartDt, todayDt);
        long criticalNcs = nonConformanceRepository.countBySeverity(NcSeverity.CRITICAL);
        long openWos = workOrderRepository.countOpenByEquipmentId(null);
        Double mttr = computeMttr(weekStartDt, todayDt);

        return new ReportData(oeeAvg, newNcs, criticalNcs, openWos, mttr);
    }

    private Double computeMttr(LocalDateTime from, LocalDateTime to) {
        List<com.industrialhub.backend.maintenance.domain.WorkOrder> completed =
                workOrderRepository.findCompletedCorrectiveInPeriod(from, to);
        if (completed.isEmpty()) return null;
        OptionalDouble avg = completed.stream()
                .mapToLong(wo -> ChronoUnit.SECONDS.between(wo.getStartedAt(), wo.getClosedAt()))
                .average();
        return avg.isPresent() ? avg.getAsDouble() / 3600.0 : null;
    }

    String buildBody(ReportData d) {
        String oee = d.oeeAvg() != null ? String.format(Locale.ROOT, "%.1f%%", d.oeeAvg() * 100) : "N/A";
        String mttr = d.mttr() != null ? String.format(Locale.ROOT, "%.1f h", d.mttr()) : "N/A";
        return String.format(
                "MSB Industrial Hub — Relatório Semanal\n" +
                "========================================\n\n" +
                "OEE médio (7 dias): %s\n" +
                "Novas NCs na semana: %d (críticas: %d)\n" +
                "OSs abertas: %d\n" +
                "MTTR da semana: %s\n\n" +
                "Acesse o sistema para mais detalhes.",
                oee, d.newNcs(), d.criticalNcs(), d.openWos(), mttr
        );
    }

    record ReportData(Double oeeAvg, long newNcs, long criticalNcs, long openWos, Double mttr) {}
}
