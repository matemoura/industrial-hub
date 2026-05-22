package com.industrialhub.backend.common.application;

import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.domain.AuditLog;
import com.industrialhub.backend.common.infrastructure.AuditLogRepository;
import com.industrialhub.backend.common.infrastructure.NotificationRepository;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Executes each data-retention block in its own @Transactional boundary,
 * so that Spring AOP proxy intercepts the transaction correctly.
 * Called by DataRetentionService — never via `this`.
 */
@Service
@RequiredArgsConstructor
public class DataRetentionExecutor {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final NonConformanceRepository nonConformanceRepository;
    private final WorkOrderRepository workOrderRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public int anonymizeEligibleUsers(LocalDateTime cutoff) {
        List<User> users = userRepository.findByDeactivatedAtBeforeAndAnonymizedFalse(cutoff);
        int count = 0;
        for (User user : users) {
            String anonUsername = "[usuario-" + user.getId().toString().substring(0, 8) + "]";
            user.setUsername(anonUsername);
            user.setEmail(null);
            user.setPassword(passwordEncoder.encode("*invalid*"));
            userRepository.save(user);
            count++;
        }
        return count;
    }

    @Transactional
    public int anonymizeEligibleAuditLogs(LocalDateTime cutoff) {
        List<AuditLog> logs = auditLogRepository.findEligibleForAnonymization(cutoff);
        List<AuditLog> anonymized = logs.stream()
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
        auditLogRepository.saveAll(anonymized);
        return anonymized.size();
    }

    @Transactional
    public int anonymizeEligibleNonConformances(LocalDateTime cutoff) {
        List<NonConformance> ncs = nonConformanceRepository.findEligibleForAnonymization(cutoff);
        for (NonConformance nc : ncs) {
            nc.setReportedBy("[anonimizado]");
            if (nc.getClosedBy() != null) {
                nc.setClosedBy("[anonimizado]");
            }
        }
        nonConformanceRepository.saveAll(ncs);
        return ncs.size();
    }

    @Transactional
    public int anonymizeEligibleWorkOrders(LocalDateTime cutoff) {
        List<WorkOrder> orders = workOrderRepository.findEligibleForAnonymization(cutoff);
        for (WorkOrder wo : orders) {
            wo.setOpenedBy("[anonimizado]");
            if (wo.getAssignedTo() != null) {
                wo.setAssignedTo("[anonimizado]");
            }
        }
        workOrderRepository.saveAll(orders);
        return orders.size();
    }

    @Transactional
    public int deleteOldNotifications(LocalDateTime cutoff) {
        return notificationRepository.deleteOlderThan(cutoff);
    }
}
