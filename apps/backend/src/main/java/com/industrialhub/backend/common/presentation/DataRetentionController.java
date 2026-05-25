package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.DataRetentionService;
import com.industrialhub.backend.common.application.dto.DataRetentionPreview;
import com.industrialhub.backend.common.application.dto.DataRetentionReport;
import com.industrialhub.backend.common.domain.DataRetentionCooldownException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/v1/admin/data-retention")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class DataRetentionController {

    private static final Duration RUN_NOW_COOLDOWN = Duration.ofMinutes(60);

    private final AtomicReference<Instant> lastRunAt = new AtomicReference<>(Instant.EPOCH);

    private final DataRetentionService dataRetentionService;

    /**
     * GET /api/v1/admin/data-retention/preview
     * Returns counts of entities eligible for anonymization/deletion (dry-run).
     */
    @GetMapping("/preview")
    public DataRetentionPreview preview() {
        return dataRetentionService.preview();
    }

    /**
     * POST /api/v1/admin/data-retention/run-now
     * Immediately executes the data retention job and returns a summary report.
     * Rate-limited to once every 5 minutes to prevent accidental double-runs.
     */
    @PostMapping("/run-now")
    public DataRetentionReport runNow() {
        Instant now = Instant.now();
        Instant last = lastRunAt.get();

        if (Duration.between(last, now).compareTo(RUN_NOW_COOLDOWN) < 0) {
            long remaining = RUN_NOW_COOLDOWN.toSeconds() - Duration.between(last, now).toSeconds();
            throw new DataRetentionCooldownException(remaining);
        }

        if (!lastRunAt.compareAndSet(last, now)) {
            Instant updatedLast = lastRunAt.get();
            long remaining = RUN_NOW_COOLDOWN.toSeconds() - Duration.between(updatedLast, now).toSeconds();
            if (remaining > 0) {
                throw new DataRetentionCooldownException(remaining);
            }
        }

        return dataRetentionService.executeAll();
    }
}
