package com.industrialhub.backend.maintenance.infrastructure;

import com.industrialhub.backend.maintenance.application.usecase.RunDueSchedulesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceSchedulerJob {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceSchedulerJob.class);

    private final RunDueSchedulesUseCase runDueSchedulesUseCase;

    public MaintenanceSchedulerJob(RunDueSchedulesUseCase runDueSchedulesUseCase) {
        this.runDueSchedulesUseCase = runDueSchedulesUseCase;
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "${app.maintenance.timezone:America/Sao_Paulo}")
    public void runDueSchedules() {
        log.info("Iniciando job de manutenção preventiva...");
        int created = runDueSchedulesUseCase.execute();
        log.info("Job de manutenção preventiva concluído. OSs criadas: {}", created);
    }
}
