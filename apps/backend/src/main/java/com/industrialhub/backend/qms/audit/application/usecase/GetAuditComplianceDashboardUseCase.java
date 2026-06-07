package com.industrialhub.backend.qms.audit.application.usecase;

import com.industrialhub.backend.qms.audit.application.dto.AuditComplianceDashboard;
import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.FindingType;
import com.industrialhub.backend.qms.audit.infrastructure.AuditChecklistItemRepository;
import com.industrialhub.backend.qms.audit.infrastructure.AuditFindingRepository;
import com.industrialhub.backend.qms.audit.infrastructure.InternalAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

@Service
public class GetAuditComplianceDashboardUseCase {

    private final InternalAuditRepository auditRepository;
    private final AuditChecklistItemRepository checklistRepository;
    private final AuditFindingRepository findingRepository;

    public GetAuditComplianceDashboardUseCase(InternalAuditRepository auditRepository,
                                               AuditChecklistItemRepository checklistRepository,
                                               AuditFindingRepository findingRepository) {
        this.auditRepository = auditRepository;
        this.checklistRepository = checklistRepository;
        this.findingRepository = findingRepository;
    }

    @Transactional(readOnly = true)
    public AuditComplianceDashboard execute() {
        int year = LocalDate.now().getYear();
        LocalDate today = LocalDate.now();
        LocalDate twelveMonthsAgo = today.minusMonths(12);

        long plannedThisYear = auditRepository.countByStatusAndYear(AuditStatus.PLANNED, year)
            + auditRepository.countByStatusAndYear(AuditStatus.IN_PROGRESS, year)
            + auditRepository.countByStatusAndYear(AuditStatus.COMPLETED, year);
        long completedThisYear = auditRepository.countByStatusAndYear(AuditStatus.COMPLETED, year);
        long overdueAudits = auditRepository.countOverdue(today);
        long openFindings = findingRepository.countOpenNonConformanceFindings();

        Map<FindingType, Long> findingsByType = new EnumMap<>(FindingType.class);
        for (FindingType type : FindingType.values()) {
            findingsByType.put(type, findingRepository.countByType(type));
        }

        long totalResponded = checklistRepository.countRespondedSince(twelveMonthsAgo);
        long conforming = checklistRepository.countConformingSince(twelveMonthsAgo);
        double conformityRate = totalResponded > 0
            ? Math.round((conforming * 100.0 / totalResponded) * 10.0) / 10.0
            : 0.0;

        return new AuditComplianceDashboard(
            plannedThisYear, completedThisYear, overdueAudits,
            openFindings, findingsByType, conformityRate
        );
    }
}
