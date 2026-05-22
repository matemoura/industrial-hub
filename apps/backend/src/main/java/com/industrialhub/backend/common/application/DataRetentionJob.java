package com.industrialhub.backend.common.application;

import com.industrialhub.backend.common.application.dto.DataRetentionReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataRetentionJob {

    private final DataRetentionService dataRetentionService;

    /**
     * Runs on the 1st of each month at 02:00 (America/Sao_Paulo).
     */
    @Scheduled(cron = "0 0 2 1 * *", zone = "America/Sao_Paulo")
    public void run() {
        log.info("DataRetentionJob started");
        DataRetentionReport report = dataRetentionService.executeAll();
        log.info("DataRetentionJob completed: users={}, auditLogs={}, ncs={}, workOrders={}, notifications={}",
            report.anonymizedUsers(),
            report.anonymizedAuditLogs(),
            report.anonymizedNonConformances(),
            report.anonymizedWorkOrders(),
            report.deletedNotifications());
    }
}
