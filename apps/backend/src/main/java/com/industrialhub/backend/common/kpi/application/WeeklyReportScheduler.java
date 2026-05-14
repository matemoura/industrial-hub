package com.industrialhub.backend.common.kpi.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WeeklyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportScheduler.class);

    private final WeeklyReportService weeklyReportService;

    public WeeklyReportScheduler(WeeklyReportService weeklyReportService) {
        this.weeklyReportService = weeklyReportService;
    }

    @Scheduled(cron = "0 0 7 * * MON", zone = "America/Sao_Paulo")
    public void sendWeeklyReport() {
        log.info("Disparando relatório semanal agendado");
        weeklyReportService.send();
    }
}
