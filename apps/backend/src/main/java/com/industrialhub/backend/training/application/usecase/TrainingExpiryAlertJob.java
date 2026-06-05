package com.industrialhub.backend.training.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TrainingExpiryAlertJob {

    private static final Logger log = LoggerFactory.getLogger(TrainingExpiryAlertJob.class);

    private final TrainingExpiryAlertUseCase alertUseCase;

    public TrainingExpiryAlertJob(TrainingExpiryAlertUseCase alertUseCase) {
        this.alertUseCase = alertUseCase;
    }

    @Scheduled(cron = "0 0 8 * * MON", zone = "America/Sao_Paulo")
    public void runWeekly() {
        log.info("Iniciando job de alertas de treinamentos vencidos/vencendo...");
        try {
            int alerted = alertUseCase.execute();
            log.info("Job de alertas de treinamentos concluído: {} notificações enviadas.", alerted);
        } catch (Exception e) {
            log.error("Erro durante job de alertas de treinamentos: {}", e.getMessage(), e);
        }
    }
}
