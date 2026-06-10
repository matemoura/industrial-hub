package com.industrialhub.backend.common;

import com.industrialhub.backend.common.application.AuditRetentionJob;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.AuditLog;
import com.industrialhub.backend.common.domain.AuditRetentionConfig;
import com.industrialhub.backend.common.infrastructure.AuditLogRepository;
import com.industrialhub.backend.common.infrastructure.AuditRetentionConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditTrailTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditRetentionConfigRepository retentionConfigRepository;

    @InjectMocks
    private AuditRetentionJob auditRetentionJob;

    @Test
    void auditLog_temCamposModule_beforeState_afterState() {
        AuditLog log = AuditLog.builder()
                .id(UUID.randomUUID())
                .timestamp(LocalDateTime.now())
                .username("admin")
                .action(AuditAction.USER_UPDATED)
                .entityType("User")
                .entityId("abc")
                .module("AUTH")
                .beforeState("{\"role\":\"OPERATOR\"}")
                .afterState("{\"role\":\"USER\"}")
                .build();

        assertThat(log.getModule()).isEqualTo("AUTH");
        assertThat(log.getBeforeState()).contains("OPERATOR");
        assertThat(log.getAfterState()).contains("USER");
    }

    @Test
    void auditRetentionJob_deletaLogsAntigos() {
        AuditRetentionConfig config = AuditRetentionConfig.builder()
                .id(1L).retentionDays(90).updatedBy("admin").build();
        when(retentionConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(auditLogRepository.deleteOlderThan(any())).thenReturn(5);

        auditRetentionJob.purge();

        verify(auditLogRepository).deleteOlderThan(any(LocalDateTime.class));
    }

    @Test
    void auditRetentionJob_usaDefault365QuandoConfigNaoExiste() {
        when(retentionConfigRepository.findById(1L)).thenReturn(Optional.empty());
        when(auditLogRepository.deleteOlderThan(any())).thenReturn(0);

        auditRetentionJob.purge();

        verify(auditLogRepository).deleteOlderThan(argThat(cutoff ->
                cutoff.isBefore(LocalDateTime.now().minusDays(364)) &&
                cutoff.isAfter(LocalDateTime.now().minusDays(366))
        ));
    }

    @Test
    void csvExport_conteudoCorretamenteMapeado() {
        AuditLog log = AuditLog.builder()
                .id(UUID.randomUUID())
                .timestamp(LocalDateTime.of(2026, 6, 10, 10, 0))
                .username("joao")
                .action(AuditAction.USER_UPDATED)
                .entityType("User")
                .entityId("user-123")
                .module("AUTH")
                .beforeState("{\"role\":\"OPERATOR\"}")
                .afterState("{\"role\":\"USER\"}")
                .build();

        String csv = buildCsv(List.of(log));

        assertThat(csv).startsWith("username,action,module,entityType,entityId,timestamp,beforeState,afterState");
        assertThat(csv).contains("joao");
        assertThat(csv).contains("USER_UPDATED");
        assertThat(csv).contains("AUTH");
    }

    private String buildCsv(List<AuditLog> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("username,action,module,entityType,entityId,timestamp,beforeState,afterState\n");
        for (AuditLog l : logs) {
            sb.append(l.getUsername()).append(',')
              .append(l.getAction().name()).append(',')
              .append(l.getModule() != null ? l.getModule() : "").append(',')
              .append(l.getEntityType()).append(',')
              .append(l.getEntityId()).append(',')
              .append(l.getTimestamp()).append(',')
              .append(l.getBeforeState() != null ? l.getBeforeState() : "").append(',')
              .append(l.getAfterState() != null ? l.getAfterState() : "").append('\n');
        }
        return sb.toString();
    }
}
