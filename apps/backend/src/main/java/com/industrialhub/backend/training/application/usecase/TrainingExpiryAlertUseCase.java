package com.industrialhub.backend.training.application.usecase;

import com.industrialhub.backend.common.application.NotificationService;
import com.industrialhub.backend.common.domain.NotificationSeverity;
import com.industrialhub.backend.training.domain.TrainingRecord;
import com.industrialhub.backend.training.infrastructure.TrainingRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TrainingExpiryAlertUseCase {

    private final TrainingRecordRepository recordRepository;
    private final NotificationService notificationService;

    public TrainingExpiryAlertUseCase(TrainingRecordRepository recordRepository,
                                      NotificationService notificationService) {
        this.recordRepository = recordRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public int execute() {
        LocalDate today = LocalDate.now();
        int alerted = 0;

        // Vencendo nos próximos 30 dias (debounce 144h)
        List<TrainingRecord> expiring = recordRepository.findExpiringBetween(today, today.plusDays(30));
        for (TrainingRecord r : expiring) {
            String title = "Certificação vencendo: " + r.getCourse().getCode();
            String body = "Sua certificação em '%s' vence em %s. Renove para manter a conformidade."
                .formatted(r.getCourse().getTitle(), r.getExpiresAt());
            alerted += notificationService.createForUserWithDebounce(
                r.getUsername(), title, body, NotificationSeverity.WARNING, 144);
        }

        // Vencidas (debounce 24h)
        List<TrainingRecord> expired = recordRepository.findExpired(today);
        for (TrainingRecord r : expired) {
            String title = "Certificação vencida: " + r.getCourse().getCode();
            String body = "Sua certificação em '%s' venceu em %s. Regularize imediatamente."
                .formatted(r.getCourse().getTitle(), r.getExpiresAt());
            alerted += notificationService.createForUserWithDebounce(
                r.getUsername(), title, body, NotificationSeverity.CRITICAL, 24);
        }

        return alerted;
    }
}
