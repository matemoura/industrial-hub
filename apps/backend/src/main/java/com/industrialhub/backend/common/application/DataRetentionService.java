package com.industrialhub.backend.common.application;

import com.industrialhub.backend.common.application.dto.AnonymizeUserResponse;
import com.industrialhub.backend.common.application.dto.DataRetentionPreview;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataRetentionService {

    private static final int USER_RETENTION_YEARS = 2;
    private static final int AUDIT_RETENTION_YEARS = 5;
    private static final int NC_RETENTION_YEARS = 5;
    private static final int WO_RETENTION_YEARS = 5;
    private static final int NOTIFICATION_RETENTION_DAYS = 90;

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final NonConformanceRepository nonConformanceRepository;
    private final WorkOrderRepository workOrderRepository;
    private final NotificationRepository notificationRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final DataRetentionExecutor executor;

    /**
     * Executes all retention blocks independently.
     * Failure in one block logs ERROR and continues the others.
     */
    public DataRetentionReport executeAll() {
        LocalDateTime executedAt = LocalDateTime.now();
        LocalDateTime userCutoff = executedAt.minusYears(USER_RETENTION_YEARS);
        LocalDateTime auditCutoff = executedAt.minusYears(AUDIT_RETENTION_YEARS);
        LocalDateTime ncCutoff = executedAt.minusYears(NC_RETENTION_YEARS);
        LocalDateTime woCutoff = executedAt.minusYears(WO_RETENTION_YEARS);
        LocalDateTime notifCutoff = executedAt.minusDays(NOTIFICATION_RETENTION_DAYS);

        int anonymizedUsers = 0;
        int anonymizedAuditLogs = 0;
        int anonymizedNonConformances = 0;
        int anonymizedWorkOrders = 0;
        int deletedNotifications = 0;

        try {
            anonymizedUsers = executor.anonymizeEligibleUsers(userCutoff);
        } catch (Exception e) {
            log.error("DataRetention: error anonymizing users", e);
        }

        try {
            anonymizedAuditLogs = executor.anonymizeEligibleAuditLogs(auditCutoff);
        } catch (Exception e) {
            log.error("DataRetention: error anonymizing audit logs", e);
        }

        try {
            anonymizedNonConformances = executor.anonymizeEligibleNonConformances(ncCutoff);
        } catch (Exception e) {
            log.error("DataRetention: error anonymizing non-conformances", e);
        }

        try {
            anonymizedWorkOrders = executor.anonymizeEligibleWorkOrders(woCutoff);
        } catch (Exception e) {
            log.error("DataRetention: error anonymizing work orders", e);
        }

        try {
            deletedNotifications = executor.deleteOldNotifications(notifCutoff);
        } catch (Exception e) {
            log.error("DataRetention: error deleting old notifications", e);
        }

        DataRetentionReport report = new DataRetentionReport(
            anonymizedUsers, anonymizedAuditLogs, anonymizedNonConformances,
            anonymizedWorkOrders, deletedNotifications, executedAt
        );

        try {
            auditService.log("system", AuditAction.DATA_RETENTION_EXECUTED,
                "DataRetention", "batch-" + executedAt.toLocalDate(),
                Map.of(
                    "anonymizedUsers", anonymizedUsers,
                    "anonymizedAuditLogs", anonymizedAuditLogs,
                    "anonymizedNonConformances", anonymizedNonConformances,
                    "anonymizedWorkOrders", anonymizedWorkOrders,
                    "deletedNotifications", deletedNotifications
                ));
        } catch (Exception e) {
            log.error("DataRetention: error recording audit log for retention execution", e);
        }

        return report;
    }

    /**
     * Returns a dry-run preview of how many entities would be affected.
     */
    public DataRetentionPreview preview() {
        LocalDateTime userCutoff = LocalDateTime.now().minusYears(USER_RETENTION_YEARS);
        LocalDateTime auditCutoff = LocalDateTime.now().minusYears(AUDIT_RETENTION_YEARS);
        LocalDateTime ncCutoff = LocalDateTime.now().minusYears(NC_RETENTION_YEARS);
        LocalDateTime woCutoff = LocalDateTime.now().minusYears(WO_RETENTION_YEARS);
        LocalDateTime notifCutoff = LocalDateTime.now().minusDays(NOTIFICATION_RETENTION_DAYS);

        return new DataRetentionPreview(
            userRepository.countInactiveNotAnonymized(userCutoff),
            auditLogRepository.countEligibleForAnonymization(auditCutoff),
            nonConformanceRepository.countEligibleForAnonymization(ncCutoff),
            workOrderRepository.countEligibleForAnonymization(woCutoff),
            notificationRepository.countOlderThan(notifCutoff)
        );
    }

    /**
     * Immediately anonymizes a specific user and their associated data.
     * Used by the "right to erasure" endpoint.
     */
    @Transactional
    public AnonymizeUserResponse anonymizeUser(UUID userId, String reason, String requesterUsername) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        // SEC-084: guard — cannot anonymize an active ADMIN
        if (user.getRole() == Role.ADMIN && user.isActive()) {
            throw new CannotAnonymizeActiveAdminException(userId);
        }

        if (user.getUsername().equals(requesterUsername)) {
            throw new SelfAnonymizationException();
        }

        if (user.getUsername().startsWith("[usuario-")) {
            throw new UserAlreadyAnonymizedException();
        }

        String originalUsername = user.getUsername();
        String anonUsername = "[usuario-" + user.getId().toString().substring(0, 8) + "]";

        // Anonymize User PII
        user.setUsername(anonUsername);
        user.setEmail(null);
        user.setPassword(passwordEncoder.encode("*invalid*"));
        user.setActive(false);
        userRepository.save(user);

        // Anonymize AuditLog entries by this username (rebuild since AuditLog is immutable)
        List<AuditLog> auditLogs = auditLogRepository.findByUsernameOrderByTimestampDesc(originalUsername);
        List<AuditLog> anonymizedLogs = auditLogs.stream()
            .map(entry -> AuditLog.builder()
                .id(entry.getId())
                .timestamp(entry.getTimestamp())
                .username("[anonimizado]")
                .action(entry.getAction())
                .entityType(entry.getEntityType())
                .entityId(entry.getEntityId())
                .details(null)
                .ipAddress(null)
                .build())
            .collect(Collectors.toList());
        auditLogRepository.saveAll(anonymizedLogs);

        // Anonymize NonConformances reported by this user
        List<NonConformance> ncs = nonConformanceRepository.findByReportedByOrderByReportedAtDesc(originalUsername);
        for (NonConformance nc : ncs) {
            nc.setReportedBy("[anonimizado]");
            if (originalUsername.equals(nc.getClosedBy())) {
                nc.setClosedBy("[anonimizado]");
            }
        }
        nonConformanceRepository.saveAll(ncs);

        // Anonymize WorkOrders opened by this user
        List<WorkOrder> workOrders = workOrderRepository.findByOpenedByOrderByOpenedAtDesc(originalUsername);
        for (WorkOrder wo : workOrders) {
            wo.setOpenedBy("[anonimizado]");
            if (originalUsername.equals(wo.getAssignedTo())) {
                wo.setAssignedTo("[anonimizado]");
            }
        }
        workOrderRepository.saveAll(workOrders);

        Map<String, Integer> affected = Map.of(
            "auditLogs", auditLogs.size(),
            "nonConformances", ncs.size(),
            "workOrders", workOrders.size()
        );

        // SEC-085: record reason in audit log
        auditService.log(requesterUsername, AuditAction.USER_ANONYMIZED,
            "User", userId.toString(),
            Map.of("reason", reason, "triggeredBy", requesterUsername, "affectedEntities", affected));

        return new AnonymizeUserResponse(true, affected);
    }
}
