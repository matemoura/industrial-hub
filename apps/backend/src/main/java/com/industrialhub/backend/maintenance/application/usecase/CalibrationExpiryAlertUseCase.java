package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.NotificationService;
import com.industrialhub.backend.common.domain.NotificationSeverity;
import com.industrialhub.backend.maintenance.domain.CalibrationSchedule;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * US-122: Alerta de calibração vencendo/vencida.
 * - nextDueAt BETWEEN today AND today+14 → WARNING, debounce 72h
 * - nextDueAt < today → CRITICAL, debounce 24h
 * Notificação pessoal por colaborador (createdBy do plano).
 */
@Service
public class CalibrationExpiryAlertUseCase {

    private final CalibrationScheduleRepository scheduleRepository;
    private final NotificationService notificationService;

    public CalibrationExpiryAlertUseCase(CalibrationScheduleRepository scheduleRepository,
                                          NotificationService notificationService) {
        this.scheduleRepository = scheduleRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public int execute() {
        LocalDate today = LocalDate.now();
        int sent = 0;

        // Planos com calibração vencendo nos próximos 14 dias → WARNING
        List<CalibrationSchedule> dueSoon = scheduleRepository
            .findByActiveTrueAndNextDueAtBetween(today, today.plusDays(14));

        for (CalibrationSchedule s : dueSoon) {
            if (s.getNextDueAt() == null) continue;
            String title = "Calibração vencendo: " + s.getEquipment().getCode();
            String body = "O plano de calibração do equipamento "
                + s.getEquipment().getName()
                + " vence em " + s.getNextDueAt() + ". Providencie a calibração.";
            sent += notificationService.createForUserWithDebounce(
                s.getCreatedBy(), title, body, NotificationSeverity.WARNING, 72);
        }

        // Planos com calibração vencida (nextDueAt < today) → CRITICAL
        List<CalibrationSchedule> overdue = scheduleRepository
            .findByActiveTrueAndNextDueAtBefore(today);

        for (CalibrationSchedule s : overdue) {
            if (s.getNextDueAt() == null) continue;
            String title = "Calibração vencida: " + s.getEquipment().getCode();
            String body = "O plano de calibração do equipamento "
                + s.getEquipment().getName()
                + " está vencido desde " + s.getNextDueAt() + ". Ação imediata necessária.";
            sent += notificationService.createForUserWithDebounce(
                s.getCreatedBy(), title, body, NotificationSeverity.CRITICAL, 24);
        }

        return sent;
    }
}
