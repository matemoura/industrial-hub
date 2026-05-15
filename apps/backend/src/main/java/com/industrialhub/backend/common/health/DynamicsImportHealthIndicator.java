package com.industrialhub.backend.common.health;

import com.industrialhub.backend.oee.infrastructure.ImportBatchRepository;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DynamicsImportHealthIndicator extends AbstractHealthIndicator {

    private final ImportBatchRepository batchRepository;

    public DynamicsImportHealthIndicator(ImportBatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        boolean hasRecentImport = batchRepository.existsByPeriodDateAfter(thirtyDaysAgo);
        if (hasRecentImport) {
            builder.up().withDetail("lastImport", "within 30 days");
        } else {
            builder.down().withDetail("warning", "No import in last 30 days");
        }
    }
}
