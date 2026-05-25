package com.industrialhub.backend.common.application;

import com.industrialhub.backend.common.application.dto.AnonymizeUserResponse;
import com.industrialhub.backend.common.application.dto.DataRetentionReport;
import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.domain.UserNotFoundException;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.AuditLog;
import com.industrialhub.backend.common.domain.CannotAnonymizeActiveAdminException;
import com.industrialhub.backend.common.domain.SelfAnonymizationException;
import com.industrialhub.backend.common.domain.UserAlreadyAnonymizedException;
import com.industrialhub.backend.common.infrastructure.AuditLogRepository;
import com.industrialhub.backend.common.infrastructure.NotificationRepository;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataRetentionServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private NonConformanceRepository nonConformanceRepository;
    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private AuditService auditService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private DataRetentionExecutor executor;

    @InjectMocks private DataRetentionService service;

    // ── Test 1: anonymizeUser() anonymizes User PII correctly ──────────────────

    @Test
    void anonymizeUser_anonymizesUserPiiCorrectly() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .username("joao.silva")
            .email("joao@empresa.com")
            .password("hashed-password")
            .role(Role.OPERATOR)
            .active(true)
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.findByUsernameOrderByTimestampDesc("joao.silva")).thenReturn(List.of());
        when(nonConformanceRepository.findByReportedByOrderByReportedAtDesc("joao.silva")).thenReturn(List.of());
        when(workOrderRepository.findByOpenedByOrderByOpenedAtDesc("joao.silva")).thenReturn(List.of());
        when(passwordEncoder.encode("*invalid*")).thenReturn("invalid-hash");

        AnonymizeUserResponse response = service.anonymizeUser(userId, "LGPD request", "admin");

        assertThat(response.anonymized()).isTrue();
        assertThat(user.getUsername()).startsWith("[usuario-");
        assertThat(user.getEmail()).isNull();
        assertThat(user.isActive()).isFalse();
        verify(passwordEncoder).encode("*invalid*");
        verify(userRepository).save(user);
    }

    // ── Test 2: anonymizeUser() anonymizes AuditLog.username to "[anonimizado]" ─

    @Test
    void anonymizeUser_anonymizesAuditLogUsernameToAnon() {
        UUID userId = UUID.randomUUID();
        String originalUsername = "maria.santos";
        User user = User.builder()
            .id(userId)
            .username(originalUsername)
            .email("maria@empresa.com")
            .password("hashed")
            .role(Role.SUPERVISOR)
            .active(false)
            .build();

        UUID logId = UUID.randomUUID();
        AuditLog existingLog = AuditLog.builder()
            .id(logId)
            .timestamp(LocalDateTime.now().minusYears(1))
            .username(originalUsername)
            .action(AuditAction.NC_CREATED)
            .entityType("NonConformance")
            .entityId(UUID.randomUUID().toString())
            .details("{\"severity\":\"HIGH\"}")
            .ipAddress("192.168.1.1")
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.findByUsernameOrderByTimestampDesc(originalUsername))
            .thenReturn(List.of(existingLog));
        when(auditLogRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(nonConformanceRepository.findByReportedByOrderByReportedAtDesc(originalUsername))
            .thenReturn(List.of());
        when(workOrderRepository.findByOpenedByOrderByOpenedAtDesc(originalUsername))
            .thenReturn(List.of());
        when(passwordEncoder.encode("*invalid*")).thenReturn("invalid-hash");

        service.anonymizeUser(userId, "LGPD request", "admin");

        verify(auditLogRepository).saveAll(argThat((List<AuditLog> logs) -> {
            assertThat(logs).hasSize(1);
            AuditLog anonymized = logs.get(0);
            assertThat(anonymized.getId()).isEqualTo(logId);
            assertThat(anonymized.getUsername()).isEqualTo("[anonimizado]");
            assertThat(anonymized.getDetails()).isNull();
            assertThat(anonymized.getIpAddress()).isNull();
            // action, entityType, entityId and timestamp must be preserved
            assertThat(anonymized.getAction()).isEqualTo(AuditAction.NC_CREATED);
            assertThat(anonymized.getEntityType()).isEqualTo("NonConformance");
            return true;
        }));
    }

    // ── Test 3: runRetentionCycle (executeAll) delegates to executor ───────────

    @Test
    void executeAll_delegatesToExecutorAndReturnsCorrectCounts() {
        when(executor.anonymizeEligibleUsers(any(LocalDateTime.class))).thenReturn(2);
        when(executor.anonymizeEligibleAuditLogs(any(LocalDateTime.class))).thenReturn(1);
        when(executor.anonymizeEligibleNonConformances(any(LocalDateTime.class))).thenReturn(0);
        when(executor.anonymizeEligibleWorkOrders(any(LocalDateTime.class))).thenReturn(0);
        when(executor.deleteOldNotifications(any(LocalDateTime.class))).thenReturn(5);

        DataRetentionReport report = service.executeAll();

        assertThat(report.anonymizedUsers()).isEqualTo(2);
        assertThat(report.anonymizedAuditLogs()).isEqualTo(1);
        assertThat(report.anonymizedNonConformances()).isEqualTo(0);
        assertThat(report.anonymizedWorkOrders()).isEqualTo(0);
        assertThat(report.deletedNotifications()).isEqualTo(5);
        assertThat(report.executedAt()).isNotNull();
    }

    // ── Test 4: ADMIN cannot anonymize their own account ──────────────────────

    @Test
    void anonymizeUser_selfAnonymization_throwsSelfAnonymizationException() {
        UUID userId = UUID.randomUUID();
        User admin = User.builder()
            .id(userId)
            .username("admin")
            .email("admin@msbbrasil.com")
            .password("hashed")
            .role(Role.OPERATOR)
            .active(true)
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.anonymizeUser(userId, "test", "admin"))
            .isInstanceOf(SelfAnonymizationException.class)
            .hasMessageContaining("própria conta");

        verify(userRepository, never()).save(any());
    }

    // ── Extra: already anonymized user returns UserAlreadyAnonymizedException ──

    @Test
    void anonymizeUser_alreadyAnonymized_throwsUserAlreadyAnonymizedException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .username("[usuario-abcd1234]")
            .email(null)
            .password("invalid-hash")
            .role(Role.OPERATOR)
            .active(false)
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.anonymizeUser(userId, "test", "admin"))
            .isInstanceOf(UserAlreadyAnonymizedException.class)
            .hasMessageContaining("anonimizado");

        verify(userRepository, never()).save(any());
    }

    // ── SEC-084: active ADMIN cannot be anonymized ────────────────────────────

    @Test
    void anonymizeUser_activeAdmin_throwsCannotAnonymizeActiveAdminException() {
        UUID userId = UUID.randomUUID();
        User activeAdmin = User.builder()
            .id(userId)
            .username("admin.ativo")
            .email("admin@msbbrasil.com")
            .password("hashed")
            .role(Role.ADMIN)
            .active(true)
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(activeAdmin));

        assertThatThrownBy(() -> service.anonymizeUser(userId, "teste", "other-admin"))
            .isInstanceOf(CannotAnonymizeActiveAdminException.class)
            .hasMessageContaining("ADMIN ativo");

        verify(userRepository, never()).save(any());
    }

    // ── SH-S25-03a: usuário inativo há menos de 2 anos NÃO deve ser anonimizado ─

    @Test
    void executeAll_userDeactivatedLessThan2YearsAgo_isNotAnonymized() {
        // executor recebe um cutoff = now - 2 anos; retorna 0 (nenhum usuário elegível)
        when(executor.anonymizeEligibleUsers(any(LocalDateTime.class))).thenReturn(0);
        when(executor.anonymizeEligibleAuditLogs(any(LocalDateTime.class))).thenReturn(0);
        when(executor.anonymizeEligibleNonConformances(any(LocalDateTime.class))).thenReturn(0);
        when(executor.anonymizeEligibleWorkOrders(any(LocalDateTime.class))).thenReturn(0);
        when(executor.deleteOldNotifications(any(LocalDateTime.class))).thenReturn(0);

        DataRetentionReport report = service.executeAll();

        assertThat(report.anonymizedUsers()).isEqualTo(0);
        // Verifica que o cutoff passado ao executor é aproximadamente now-2anos
        verify(executor).anonymizeEligibleUsers(argThat(cutoff ->
            cutoff.isBefore(LocalDateTime.now().minusYears(2).plusSeconds(5)) &&
            cutoff.isAfter(LocalDateTime.now().minusYears(2).minusSeconds(5))
        ));
    }

    // ── SEC-085: anonymizeUser records reason in AuditLog ────────────────────

    @Test
    void anonymizeUser_recordsReasonInAuditLog() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .username("jose.silva")
            .email("jose@empresa.com")
            .password("hashed")
            .role(Role.OPERATOR)
            .active(false)
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.findByUsernameOrderByTimestampDesc("jose.silva")).thenReturn(List.of());
        when(nonConformanceRepository.findByReportedByOrderByReportedAtDesc("jose.silva")).thenReturn(List.of());
        when(workOrderRepository.findByOpenedByOrderByOpenedAtDesc("jose.silva")).thenReturn(List.of());
        when(passwordEncoder.encode("*invalid*")).thenReturn("invalid-hash");

        service.anonymizeUser(userId, "LGPD request - Article 17", "admin");

        verify(auditService).log(
            eq("admin"),
            eq(AuditAction.USER_ANONYMIZED),
            eq("User"),
            eq(userId.toString()),
            argThat(details -> {
                @SuppressWarnings("unchecked")
                var map = (java.util.Map<String, ?>) details;
                return "LGPD request - Article 17".equals(map.get("reason"))
                    && "admin".equals(map.get("triggeredBy"));
            })
        );
    }

    // ── SH-S25-03b: falha em um bloco não cancela os demais ──────────────────

    @Test
    void executeAll_oneBlockFails_doesNotCancelRemainingBlocks() {
        when(executor.anonymizeEligibleUsers(any(LocalDateTime.class)))
            .thenThrow(new RuntimeException("simulated DB failure"));
        when(executor.anonymizeEligibleAuditLogs(any(LocalDateTime.class))).thenReturn(3);
        when(executor.anonymizeEligibleNonConformances(any(LocalDateTime.class))).thenReturn(1);
        when(executor.anonymizeEligibleWorkOrders(any(LocalDateTime.class))).thenReturn(2);
        when(executor.deleteOldNotifications(any(LocalDateTime.class))).thenReturn(10);

        DataRetentionReport report = service.executeAll();

        // users block failed — count stays 0, but all other blocks ran
        assertThat(report.anonymizedUsers()).isEqualTo(0);
        assertThat(report.anonymizedAuditLogs()).isEqualTo(3);
        assertThat(report.anonymizedNonConformances()).isEqualTo(1);
        assertThat(report.anonymizedWorkOrders()).isEqualTo(2);
        assertThat(report.deletedNotifications()).isEqualTo(10);

        verify(executor).anonymizeEligibleUsers(any(LocalDateTime.class));
        verify(executor).anonymizeEligibleAuditLogs(any(LocalDateTime.class));
        verify(executor).anonymizeEligibleNonConformances(any(LocalDateTime.class));
        verify(executor).anonymizeEligibleWorkOrders(any(LocalDateTime.class));
        verify(executor).deleteOldNotifications(any(LocalDateTime.class));
    }
}
