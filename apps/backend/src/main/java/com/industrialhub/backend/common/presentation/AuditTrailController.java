package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.dto.AuditLogResponse;
import com.industrialhub.backend.common.application.dto.AuditRetentionConfigResponse;
import com.industrialhub.backend.common.application.dto.UpdateRetentionRequest;
import com.industrialhub.backend.common.domain.AuditLog;
import com.industrialhub.backend.common.domain.AuditRetentionConfig;
import com.industrialhub.backend.common.domain.InvalidRetentionDaysException;
import com.industrialhub.backend.common.infrastructure.AuditLogRepository;
import com.industrialhub.backend.common.infrastructure.AuditRetentionConfigRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AuditTrailController {

    private static final int DEFAULT_RETENTION_DAYS = 365;

    private final AuditLogRepository auditLogRepository;
    private final AuditRetentionConfigRepository retentionConfigRepository;

    public AuditTrailController(AuditLogRepository auditLogRepository,
                               AuditRetentionConfigRepository retentionConfigRepository) {
        this.auditLogRepository = auditLogRepository;
        this.retentionConfigRepository = retentionConfigRepository;
    }

    @GetMapping
    public Page<AuditLogResponse> listAuditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        LocalDateTime effectiveFrom = from != null ? from : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime effectiveTo   = to   != null ? to   : LocalDateTime.of(9999, 12, 31, 23, 59);
        return auditLogRepository.findByFilters(username, module, action, effectiveFrom, effectiveTo, pageable)
                .map(AuditLogResponse::from);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        LocalDateTime exportFrom = from != null ? from : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime exportTo   = to   != null ? to   : LocalDateTime.of(9999, 12, 31, 23, 59);
        List<AuditLog> logs = auditLogRepository
                .findByFilters(username, module, action, exportFrom, exportTo, PageRequest.of(0, 10000))
                .getContent();

        byte[] csv = buildCsv(logs);

        String filename = "audit-" + (from != null ? from.toLocalDate() : "all") +
                "-to-" + (to != null ? to.toLocalDate() : "now") + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/retention")
    public AuditRetentionConfigResponse getRetention() {
        return retentionConfigRepository.findById(1L)
                .map(AuditRetentionConfigResponse::from)
                .orElseGet(AuditRetentionConfigResponse::defaultConfig);
    }

    @PutMapping("/retention")
    public AuditRetentionConfigResponse updateRetention(@Valid @RequestBody UpdateRetentionRequest request,
                                                         Authentication auth) {
        int days = request.retentionDays();
        if (days < 30 || days > 3650) {
            throw new InvalidRetentionDaysException(days);
        }

        AuditRetentionConfig config = retentionConfigRepository.findById(1L)
                .orElseGet(() -> AuditRetentionConfig.builder().id(1L).build());
        config.setRetentionDays(days);
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(auth.getName());

        return AuditRetentionConfigResponse.from(retentionConfigRepository.save(config));
    }

    private byte[] buildCsv(List<AuditLog> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("username,action,module,entityType,entityId,timestamp,beforeState,afterState\n");
        for (AuditLog l : logs) {
            sb.append(csvField(l.getUsername())).append(',')
              .append(l.getAction().name()).append(',')
              .append(csvField(l.getModule())).append(',')
              .append(csvField(l.getEntityType())).append(',')
              .append(csvField(l.getEntityId())).append(',')
              .append(l.getTimestamp()).append(',')
              .append(csvField(l.getBeforeState())).append(',')
              .append(csvField(l.getAfterState())).append('\n');
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String csvField(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
