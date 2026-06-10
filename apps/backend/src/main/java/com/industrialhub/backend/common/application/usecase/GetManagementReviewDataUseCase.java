package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.ManagementReviewData;
import com.industrialhub.backend.common.changes.domain.ChangeStatus;
import com.industrialhub.backend.common.changes.infrastructure.ChangeRequestRepository;
import com.industrialhub.backend.common.domain.InvalidManagementReviewPeriodException;
import com.industrialhub.backend.maintenance.domain.CalibrationResult;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationRecordRepository;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationScheduleRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import com.industrialhub.backend.oee.application.usecase.GetOeeSummaryUseCase;
import com.industrialhub.backend.oee.application.usecase.GroupBy;
import com.industrialhub.backend.qms.audit.infrastructure.AuditFindingRepository;
import com.industrialhub.backend.qms.audit.infrastructure.InternalAuditRepository;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaint;
import com.industrialhub.backend.qms.complaints.infrastructure.CustomerComplaintRepository;
import com.industrialhub.backend.qms.domain.ActionStatus;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import com.industrialhub.backend.qms.risk.infrastructure.RiskItemRepository;
import com.industrialhub.backend.training.application.usecase.GetTrainingComplianceSummaryUseCase;
import com.industrialhub.backend.training.infrastructure.TrainingRecordRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class GetManagementReviewDataUseCase {

    private final NonConformanceRepository ncRepository;
    private final CorrectiveActionRepository capaRepository;
    private final CustomerComplaintRepository complaintRepository;
    private final InternalAuditRepository auditRepository;
    private final AuditFindingRepository findingRepository;
    private final CalibrationScheduleRepository calibScheduleRepository;
    private final CalibrationRecordRepository calibRecordRepository;
    private final TrainingRecordRepository trainingRecordRepository;
    private final GetTrainingComplianceSummaryUseCase trainingComplianceUseCase;
    private final RiskItemRepository riskRepository;
    private final ChangeRequestRepository changeRepository;
    private final WorkOrderRepository workOrderRepository;
    private final GetOeeSummaryUseCase oeeSummaryUseCase;

    public GetManagementReviewDataUseCase(
        NonConformanceRepository ncRepository,
        CorrectiveActionRepository capaRepository,
        CustomerComplaintRepository complaintRepository,
        InternalAuditRepository auditRepository,
        AuditFindingRepository findingRepository,
        CalibrationScheduleRepository calibScheduleRepository,
        CalibrationRecordRepository calibRecordRepository,
        TrainingRecordRepository trainingRecordRepository,
        GetTrainingComplianceSummaryUseCase trainingComplianceUseCase,
        RiskItemRepository riskRepository,
        ChangeRequestRepository changeRepository,
        WorkOrderRepository workOrderRepository,
        GetOeeSummaryUseCase oeeSummaryUseCase
    ) {
        this.ncRepository = ncRepository;
        this.capaRepository = capaRepository;
        this.complaintRepository = complaintRepository;
        this.auditRepository = auditRepository;
        this.findingRepository = findingRepository;
        this.calibScheduleRepository = calibScheduleRepository;
        this.calibRecordRepository = calibRecordRepository;
        this.trainingRecordRepository = trainingRecordRepository;
        this.trainingComplianceUseCase = trainingComplianceUseCase;
        this.riskRepository = riskRepository;
        this.changeRepository = changeRepository;
        this.workOrderRepository = workOrderRepository;
        this.oeeSummaryUseCase = oeeSummaryUseCase;
    }

    @Cacheable(value = "management-review", key = "#from.toString() + '-' + #to.toString()")
    @Transactional(readOnly = true)
    public ManagementReviewData execute(LocalDate from, LocalDate to, String principal) {
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw new InvalidManagementReviewPeriodException(
                "Período máximo de 366 dias para análise crítica");
        }

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay();

        return new ManagementReviewData(
            buildNcSummary(from, to, fromDt, toDt),
            buildCapaSummary(),
            buildComplaintSummary(from, to),
            buildAuditSummary(from, to),
            buildCalibrationSummary(from, to),
            buildTrainingSummary(),
            buildRiskSummary(fromDt, toDt),
            buildChangeSummary(),
            buildKpiSnapshot(to)
        );
    }

    private ManagementReviewData.NcSummary buildNcSummary(
            LocalDate from, LocalDate to, LocalDateTime fromDt, LocalDateTime toDt) {
        long totalReported = ncRepository.countInPeriod(fromDt, toDt);
        long criticalOpen = ncRepository.countCriticalOpen();

        List<NonConformance> closedInPeriod = ncRepository.findClosedBetween(fromDt, toDt);
        Double avgResolutionDays = closedInPeriod.isEmpty() ? null :
            closedInPeriod.stream()
                .filter(nc -> nc.getReportedAt() != null && nc.getClosedAt() != null)
                .mapToLong(nc -> ChronoUnit.DAYS.between(nc.getReportedAt(), nc.getClosedAt()))
                .average()
                .orElse(0.0);

        Map<String, Integer> byStatus = new HashMap<>();
        for (NcStatus s : NcStatus.values()) {
            byStatus.put(s.name(), (int) ncRepository.countByStatus(s));
        }

        Map<String, Integer> bySeverity = new HashMap<>();
        for (NcSeverity sev : NcSeverity.values()) {
            bySeverity.put(sev.name(), (int) ncRepository.countBySeverity(sev));
        }

        return new ManagementReviewData.NcSummary(
            (int) totalReported, (int) criticalOpen, avgResolutionDays, byStatus, bySeverity);
    }

    private ManagementReviewData.CapaSummary buildCapaSummary() {
        long totalOpen = capaRepository.countByStatusIn(
            List.of(ActionStatus.PENDING, ActionStatus.PENDING_EFFECTIVENESS));
        long overdueCount = capaRepository.countOverdue(LocalDate.now());

        List<Object[]> statusCounts = capaRepository.countByStatus();
        long totalDone = statusCounts.stream()
            .filter(row -> "DONE".equals(row[0].toString()))
            .mapToLong(row -> ((Number) row[1]).longValue())
            .sum();
        long total = statusCounts.stream()
            .mapToLong(row -> ((Number) row[1]).longValue())
            .sum();
        Double effectivenessRate = total == 0 ? null : (totalDone * 100.0) / total;

        return new ManagementReviewData.CapaSummary(
            (int) totalOpen, (int) overdueCount, effectivenessRate);
    }

    private ManagementReviewData.ComplaintSummary buildComplaintSummary(LocalDate from, LocalDate to) {
        List<CustomerComplaint> complaints = complaintRepository.findByReportedDateBetween(from, to);
        int total = complaints.size();
        int reportedToAnvisa = (int) complaints.stream().filter(CustomerComplaint::isReportedToAnvisa).count();
        Double avgResolutionDays = complaints.stream()
            .filter(c -> c.getClosedAt() != null && c.getReportedDate() != null)
            .mapToLong(c -> ChronoUnit.DAYS.between(c.getReportedDate(), c.getClosedAt().toLocalDate()))
            .average()
            .orElse(0.0);
        if (complaints.stream().noneMatch(c -> c.getClosedAt() != null)) {
            avgResolutionDays = null;
        }

        return new ManagementReviewData.ComplaintSummary(total, reportedToAnvisa, avgResolutionDays);
    }

    private ManagementReviewData.AuditSummary buildAuditSummary(LocalDate from, LocalDate to) {
        long completed = auditRepository.countCompletedBetween(from, to);
        long plannedNotDone = auditRepository.countPlannedNotDone(LocalDate.now());
        long overdueAudits = auditRepository.countOverdue(LocalDate.now());
        long nonConformingFindings = findingRepository.countOpenNonConformanceFindings();

        long totalFindings = findingRepository.count();
        Double conformityRate = totalFindings == 0 ? 100.0
            : ((totalFindings - nonConformingFindings) * 100.0) / totalFindings;

        return new ManagementReviewData.AuditSummary(
            (int) completed, (int) plannedNotDone, (int) overdueAudits,
            (int) nonConformingFindings, conformityRate);
    }

    private ManagementReviewData.CalibrationSummary buildCalibrationSummary(LocalDate from, LocalDate to) {
        long overdueSchedules = calibScheduleRepository.countByActiveTrueAndNextDueAtBefore(LocalDate.now());
        long outOfToleranceCount = calibRecordRepository.countByResultAndCalibratedAtBetween(
            CalibrationResult.OUT_OF_TOLERANCE, from, to);
        long totalRecords = calibRecordRepository.countByCalibratedAtBetween(from, to);
        Double complianceRate = totalRecords == 0 ? 100.0
            : ((totalRecords - outOfToleranceCount) * 100.0) / totalRecords;

        return new ManagementReviewData.CalibrationSummary(
            (int) overdueSchedules, (int) outOfToleranceCount, complianceRate);
    }

    private ManagementReviewData.TrainingSummary buildTrainingSummary() {
        var compliance = trainingComplianceUseCase.execute();
        int expiringIn30Days = trainingRecordRepository.findExpiringBetween(
            LocalDate.now(), LocalDate.now().plusDays(30)).size();

        return new ManagementReviewData.TrainingSummary(
            compliance.valid(),
            compliance.expiring(),
            compliance.expired() + compliance.missing(),
            expiringIn30Days
        );
    }

    private ManagementReviewData.RiskSummary buildRiskSummary(LocalDateTime fromDt, LocalDateTime toDt) {
        long totalRisks = riskRepository.count();
        long criticalOpen = riskRepository.countCriticalOpen();
        long mitigatedInPeriod = riskRepository.countMitigatedInPeriod(fromDt, toDt);
        Double avgRpn = riskRepository.findAvgRpn();

        return new ManagementReviewData.RiskSummary(
            (int) totalRisks, (int) criticalOpen, (int) mitigatedInPeriod, avgRpn);
    }

    private ManagementReviewData.ChangeSummary buildChangeSummary() {
        long submitted = changeRepository.countByStatusIn(List.of(ChangeStatus.SUBMITTED));
        long approved = changeRepository.countByStatusIn(List.of(ChangeStatus.APPROVED));
        long rejected = changeRepository.countByStatusIn(List.of(ChangeStatus.REJECTED));
        long implemented = changeRepository.countByStatusIn(List.of(ChangeStatus.IMPLEMENTED));
        long pending = changeRepository.countByStatusIn(
            List.of(ChangeStatus.DRAFT, ChangeStatus.UNDER_REVIEW));

        return new ManagementReviewData.ChangeSummary(
            (int) submitted, (int) approved, (int) rejected, (int) implemented, (int) pending);
    }

    private ManagementReviewData.KpiSnapshot buildKpiSnapshot(LocalDate to) {
        long openNcs = ncRepository.countOpen();
        long openWorkOrders = workOrderRepository.countOpen();

        LocalDate oeeFrom = to.minusDays(30);
        Double oee30Days = null;
        try {
            var summaries = oeeSummaryUseCase.execute(oeeFrom, to, GroupBy.DAY);
            if (!summaries.isEmpty()) {
                oee30Days = summaries.stream()
                    .map(s -> s.avgAvailability())
                    .filter(Objects::nonNull)
                    .mapToDouble(BigDecimal::doubleValue)
                    .average()
                    .orElse(0.0);
                oee30Days = Math.round(oee30Days * 10000.0) / 100.0;
            }
        } catch (Exception ignored) {
            // OEE sem dados no período — retorna null
        }

        return new ManagementReviewData.KpiSnapshot(oee30Days, (int) openNcs, (int) openWorkOrders);
    }
}
