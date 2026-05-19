package com.industrialhub.backend.common.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlertEvaluatorJob {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluatorJob.class);

    private final AlertEvaluatorUseCase alertEvaluatorUseCase;

    public AlertEvaluatorJob(AlertEvaluatorUseCase alertEvaluatorUseCase) {
        this.alertEvaluatorUseCase = alertEvaluatorUseCase;
    }

    @Scheduled(cron = "0 0/30 * * * *", zone = "America/Sao_Paulo")
    public void run() {
        log.info("Iniciando avaliação de alertas...");
        try {
            int fired = alertEvaluatorUseCase.execute();
            log.info("Avaliação de alertas concluída: {} alertas disparados.", fired);
        } catch (Exception e) {
            log.error("Erro durante avaliação de alertas: {}", e.getMessage(), e);
        }
    }
}
