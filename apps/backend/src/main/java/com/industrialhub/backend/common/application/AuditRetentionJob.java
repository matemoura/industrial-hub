package com.industrialhub.backend.common.application;

import com.industrialhub.backend.common.infrastructure.AuditLogRepository;
import com.industrialhub.backend.common.infrastructure.AuditRetentionConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);
    private static final int DEFAULT_RETENTION_DAYS = 365;

    private final AuditLogRepository auditLogRepository;
    private final AuditRetentionConfigRepository retentionConfigRepository;

    public AuditRetentionJob(AuditLogRepository auditLogRepository,
                              AuditRetentionConfigRepository retentionConfigRepository) {
        this.auditLogRepository = auditLogRepository;
        this.retentionConfigRepository = retentionConfigRepository;
    }

    @Scheduled(cron = "0 0 3 * * SUN", zone = "America/Sao_Paulo")
    public void purge() {
        int retentionDays = retentionConfigRepository.findById(1L)
                .map(c -> c.getRetentionDays())
                .orElse(DEFAULT_RETENTION_DAYS);

        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = auditLogRepository.deleteOlderThan(cutoff);
        log.info("AuditRetentionJob: {} registros removidos (retenção={} dias, cutoff={})",
                deleted, retentionDays, cutoff);
    }
}
