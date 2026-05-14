package com.industrialhub.backend.common.kpi.application;

import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import com.industrialhub.backend.oee.domain.RecordType;
import com.industrialhub.backend.oee.domain.TimeRecord;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

@Service
public class WeeklyReportService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    private final UserRepository userRepository;
    private final TimeRecordRepository timeRecordRepository;
    private final NonConformanceRepository nonConformanceRepository;
    private final WorkOrderRepository workOrderRepository;

    @Value("${mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${spring.mail.username:noreply@industrialhub.com}")
    private String fromAddress;

    public WeeklyReportService(UserRepository userRepository,
                                TimeRecordRepository timeRecordRepository,
                                NonConformanceRepository nonConformanceRepository,
                                WorkOrderRepository workOrderRepository) {
        this.userRepository = userRepository;
        this.timeRecordRepository = timeRecordRepository;
        this.nonConformanceRepository = nonConformanceRepository;
        this.workOrderRepository = workOrderRepository;
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
                .map(u -> u.getEmail())
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

    private ReportData collectData() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);
        LocalDateTime weekStartDt = weekStart.atStartOfDay();
        LocalDateTime todayDt = today.plusDays(1).atStartOfDay();

        Double oeeAvg = computeOeeAvg(weekStart, today);
        long newNcs = nonConformanceRepository.countInPeriod(weekStartDt, todayDt);
        long criticalNcs = nonConformanceRepository.countBySeverity(NcSeverity.CRITICAL);
        long openWos = workOrderRepository.countOpenByEquipmentId(null);
        Double mttr = computeMttr(weekStart, today);

        return new ReportData(oeeAvg, newNcs, criticalNcs, openWos, mttr);
    }

    private Double computeOeeAvg(LocalDate start, LocalDate end) {
        List<TimeRecord> records = timeRecordRepository.findByPeriodAndOptionalWorker(start, end, null);
        if (records.isEmpty()) return null;

        Map<String, List<TimeRecord>> byWorkerDay = records.stream()
                .collect(Collectors.groupingBy(r -> r.getWorkerId() + "|" + r.getProfileDate()));

        List<Double> avails = byWorkerDay.values().stream()
                .map(this::availabilityForDay)
                .filter(Objects::nonNull)
                .toList();

        return avails.isEmpty() ? null : avails.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private Double availabilityForDay(List<TimeRecord> dayRecords) {
        double productive = dayRecords.stream()
                .filter(r -> r.getRecordType() == RecordType.PROCESSO)
                .map(r -> r.getHours() != null ? r.getHours() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .doubleValue();

        LocalDateTime entry = dayRecords.stream()
                .filter(r -> r.getRecordType() == RecordType.REGISTRO_ENTRADA)
                .map(TimeRecord::getStartTime)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);

        LocalDateTime exit = dayRecords.stream()
                .filter(r -> r.getRecordType() == RecordType.REGISTRO_SAIDA)
                .map(r -> r.getEndTime() != null ? r.getEndTime() : r.getStartTime())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        if (entry == null || exit == null || !exit.isAfter(entry)) return null;
        double shiftHours = java.time.Duration.between(entry, exit).toMinutes() / 60.0;
        return shiftHours == 0 ? null : productive / shiftHours;
    }

    private Double computeMttr(LocalDate start, LocalDate end) {
        // MTTR from work orders closed in the week
        List<com.industrialhub.backend.maintenance.domain.WorkOrder> completed =
                workOrderRepository.findCompletedCorrectiveForMetrics(null).stream()
                        .filter(wo -> wo.getClosedAt() != null &&
                                !wo.getClosedAt().toLocalDate().isBefore(start) &&
                                !wo.getClosedAt().toLocalDate().isAfter(end))
                        .toList();
        if (completed.isEmpty()) return null;
        OptionalDouble avg = completed.stream()
                .mapToLong(wo -> ChronoUnit.SECONDS.between(wo.getStartedAt(), wo.getClosedAt()))
                .average();
        return avg.isPresent() ? avg.getAsDouble() / 3600.0 : null;
    }

    private String buildBody(ReportData d) {
        String oee = d.oeeAvg() != null ? String.format("%.1f%%", d.oeeAvg() * 100) : "N/A";
        String mttr = d.mttr() != null ? String.format("%.1f h", d.mttr()) : "N/A";
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

    private record ReportData(Double oeeAvg, long newNcs, long criticalNcs, long openWos, Double mttr) {}
}
