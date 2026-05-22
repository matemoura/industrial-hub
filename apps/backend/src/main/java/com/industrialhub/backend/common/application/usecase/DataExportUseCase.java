package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.UserDataExportResponse;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.domain.UserNotFoundException;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.domain.UserAlreadyAnonymizedException;
import com.industrialhub.backend.common.domain.AuditLog;
import com.industrialhub.backend.common.infrastructure.AuditLogRepository;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataExportUseCase {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final NonConformanceRepository nonConformanceRepository;
    private final WorkOrderRepository workOrderRepository;

    @Transactional(readOnly = true)
    public UserDataExportResponse execute(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException(username));

        // SH-S25-02: guard — export of an already-anonymized user is not allowed
        if (user.getUsername().startsWith("[usuario-")) {
            throw new UserAlreadyAnonymizedException();
        }

        List<NonConformance> ncs = nonConformanceRepository.findByReportedByOrderByReportedAtDesc(username);
        List<WorkOrder> workOrders = workOrderRepository.findByOpenedByOrderByOpenedAtDesc(username);
        List<AuditLog> auditLogs = auditLogRepository.findByUsernameOrderByTimestampDesc(username);

        return new UserDataExportResponse(
            LocalDateTime.now(),
            new UserDataExportResponse.ProfileSummary(
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.isActive()
            ),
            ncs.stream().map(nc -> new UserDataExportResponse.NcSummaryForExport(
                nc.getId(),
                nc.getTitle(),
                nc.getType().name(),
                nc.getSeverity().name(),
                nc.getStatus().name(),
                nc.getReportedAt()
            )).collect(Collectors.toList()),
            workOrders.stream().map(wo -> new UserDataExportResponse.WorkOrderSummaryForExport(
                wo.getId(),
                wo.getTitle(),
                wo.getType().name(),
                wo.getPriority().name(),
                wo.getStatus().name(),
                wo.getOpenedAt()
            )).collect(Collectors.toList()),
            auditLogs.stream().map(al -> new UserDataExportResponse.AuditLogSummaryForExport(
                al.getId(),
                al.getAction().name(),
                al.getEntityType(),
                al.getEntityId(),
                al.getTimestamp()
            )).collect(Collectors.toList())
        );
    }
}
