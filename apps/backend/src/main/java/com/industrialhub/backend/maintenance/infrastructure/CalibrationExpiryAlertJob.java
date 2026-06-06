package com.industrialhub.backend.maintenance.infrastructure;

import com.industrialhub.backend.maintenance.application.usecase.CalibrationExpiryAlertUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * US-122: Job diário de alerta de calibração vencendo/vencida.
 * Executa às 07:00 todos os dias (America/Sao_Paulo).
 */
@Component
public class CalibrationExpiryAlertJob {

    private static final Logger log = LoggerFactory.getLogger(CalibrationExpiryAlertJob.class);

    private final CalibrationExpiryAlertUseCase alertUseCase;

    public CalibrationExpiryAlertJob(CalibrationExpiryAlertUseCase alertUseCase) {
        this.alertUseCase = alertUseCase;
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "America/Sao_Paulo")
    public void run() {
        log.info("Iniciando job de alertas de calibração...");
        int sent = alertUseCase.execute();
        log.info("Job de alertas de calibração concluído. Alertas enviados: {}", sent);
    }

    /** Permite disparo manual via endpoint ADMIN. */
    public int runNow() {
        log.info("Disparo manual do job de alertas de calibração.");
        int sent = alertUseCase.execute();
        log.info("Job manual concluído. Alertas enviados: {}", sent);
        return sent;
    }
}
