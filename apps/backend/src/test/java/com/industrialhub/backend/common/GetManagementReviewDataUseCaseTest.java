package com.industrialhub.backend.common;

import com.industrialhub.backend.common.application.usecase.GetManagementReviewDataUseCase;
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
import com.industrialhub.backend.qms.complaints.infrastructure.CustomerComplaintRepository;
import com.industrialhub.backend.qms.domain.ActionStatus;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import com.industrialhub.backend.qms.risk.infrastructure.RiskItemRepository;
import com.industrialhub.backend.training.application.dto.TrainingComplianceSummary;
import com.industrialhub.backend.training.application.usecase.GetTrainingComplianceSummaryUseCase;
import com.industrialhub.backend.training.infrastructure.TrainingRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.annotation.Cacheable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetManagementReviewDataUseCaseTest {

    @Mock NonConformanceRepository ncRepository;
    @Mock CorrectiveActionRepository capaRepository;
    @Mock CustomerComplaintRepository complaintRepository;
    @Mock InternalAuditRepository auditRepository;
    @Mock AuditFindingRepository findingRepository;
    @Mock CalibrationScheduleRepository calibScheduleRepository;
    @Mock CalibrationRecordRepository calibRecordRepository;
    @Mock TrainingRecordRepository trainingRecordRepository;
    @Mock GetTrainingComplianceSummaryUseCase trainingComplianceUseCase;
    @Mock RiskItemRepository riskRepository;
    @Mock ChangeRequestRepository changeRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock GetOeeSummaryUseCase oeeSummaryUseCase;

    private GetManagementReviewDataUseCase useCase;

    private final LocalDate from = LocalDate.of(2026, 1, 1);
    private final LocalDate to   = LocalDate.of(2026, 12, 31);
    private final String principal = "admin";

    @BeforeEach
    void setUp() {
        useCase = new GetManagementReviewDataUseCase(
            ncRepository, capaRepository, complaintRepository,
            auditRepository, findingRepository,
            calibScheduleRepository, calibRecordRepository,
            trainingRecordRepository, trainingComplianceUseCase,
            riskRepository, changeRepository, workOrderRepository,
            oeeSummaryUseCase
        );

        // NCs
        when(ncRepository.countInPeriod(any(), any())).thenReturn(5L);
        when(ncRepository.countCriticalOpen()).thenReturn(1L);
        when(ncRepository.findClosedBetween(any(), any())).thenReturn(List.of());
        when(ncRepository.countByStatus(any(NcStatus.class))).thenReturn(0L);
        when(ncRepository.countBySeverity(any(NcSeverity.class))).thenReturn(0L);
        when(ncRepository.countOpen()).thenReturn(3L);

        // CAPAs
        when(capaRepository.countByStatusIn(anyList())).thenReturn(2L);
        when(capaRepository.countOverdue(any())).thenReturn(0L);
        when(capaRepository.countByStatus()).thenReturn(List.of());

        // Complaints
        when(complaintRepository.findByReportedDateBetween(any(), any())).thenReturn(List.of());

        // Audits
        when(auditRepository.countCompletedBetween(any(), any())).thenReturn(2L);
        when(auditRepository.countPlannedNotDone(any())).thenReturn(0L);
        when(auditRepository.countOverdue(any())).thenReturn(0L);
        when(findingRepository.countOpenNonConformanceFindings()).thenReturn(0L);
        when(findingRepository.count()).thenReturn(10L);

        // Calibrations
        when(calibScheduleRepository.countByActiveTrueAndNextDueAtBefore(any())).thenReturn(0L);
        when(calibRecordRepository.countByResultAndCalibratedAtBetween(
            any(CalibrationResult.class), any(), any())).thenReturn(0L);
        when(calibRecordRepository.countByCalibratedAtBetween(any(), any())).thenReturn(5L);

        // Training
        when(trainingComplianceUseCase.execute()).thenReturn(
            new TrainingComplianceSummary(10, 40, 35, 3, 1, 1, 87.5));
        when(trainingRecordRepository.findExpiringBetween(any(), any())).thenReturn(List.of());

        // Risks
        when(riskRepository.count()).thenReturn(8L);
        when(riskRepository.countCriticalOpen()).thenReturn(0L);
        when(riskRepository.countMitigatedInPeriod(any(), any())).thenReturn(2L);
        when(riskRepository.findAvgRpn()).thenReturn(45.0);

        // Changes
        when(changeRepository.countByStatusIn(anyList())).thenReturn(1L);

        // Work orders
        when(workOrderRepository.countOpen()).thenReturn(4L);

        // OEE
        when(oeeSummaryUseCase.execute(any(), any(), any(GroupBy.class))).thenReturn(List.of());
    }

    @Test
    void execute_shouldConsultAllRepositories() {
        var result = useCase.execute(from, to, principal);

        assertThat(result).isNotNull();

        verify(ncRepository, atLeastOnce()).countInPeriod(any(LocalDateTime.class), any(LocalDateTime.class));
        verify(ncRepository, atLeastOnce()).countCriticalOpen();
        verify(capaRepository, atLeastOnce()).countByStatusIn(anyList());
        verify(capaRepository, atLeastOnce()).countOverdue(any(LocalDate.class));
        verify(complaintRepository, atLeastOnce()).findByReportedDateBetween(any(LocalDate.class), any(LocalDate.class));
        verify(auditRepository, atLeastOnce()).countCompletedBetween(any(LocalDate.class), any(LocalDate.class));
        verify(findingRepository, atLeastOnce()).countOpenNonConformanceFindings();
        verify(calibScheduleRepository, atLeastOnce()).countByActiveTrueAndNextDueAtBefore(any(LocalDate.class));
        verify(calibRecordRepository, atLeastOnce()).countByResultAndCalibratedAtBetween(
            any(CalibrationResult.class), any(LocalDate.class), any(LocalDate.class));
        verify(trainingComplianceUseCase, atLeastOnce()).execute();
        verify(riskRepository, atLeastOnce()).countCriticalOpen();
        verify(changeRepository, atLeastOnce()).countByStatusIn(anyList());
        verify(workOrderRepository, atLeastOnce()).countOpen();
    }

    @Test
    void execute_shouldThrowWhenPeriodExceeds366Days() {
        LocalDate farFuture = from.plusYears(2);

        assertThatThrownBy(() -> useCase.execute(from, farFuture, principal))
            .isInstanceOf(InvalidManagementReviewPeriodException.class)
            .hasMessageContaining("366 dias");
    }

    @Test
    void execute_methodShouldBeCacheableOnManagementReviewCache() throws NoSuchMethodException {
        var method = GetManagementReviewDataUseCase.class.getMethod(
            "execute", LocalDate.class, LocalDate.class, String.class);
        assertThat(method.isAnnotationPresent(Cacheable.class)).isTrue();
        var cacheable = method.getAnnotation(Cacheable.class);
        assertThat(cacheable.value()).contains("management-review");
    }

    @Test
    void execute_shouldReturnAllSectionsNonNull() {
        var result = useCase.execute(from, to, principal);

        assertThat(result.ncSummary()).isNotNull();
        assertThat(result.capaSummary()).isNotNull();
        assertThat(result.complaintSummary()).isNotNull();
        assertThat(result.auditSummary()).isNotNull();
        assertThat(result.calibrationSummary()).isNotNull();
        assertThat(result.trainingSummary()).isNotNull();
        assertThat(result.riskSummary()).isNotNull();
        assertThat(result.changeSummary()).isNotNull();
        assertThat(result.kpiSummary()).isNotNull();
    }

    @Test
    void execute_shouldAcceptPeriodOfExactly366Days() {
        LocalDate end = from.plusDays(366);

        var result = useCase.execute(from, end, principal);

        assertThat(result).isNotNull();
    }

    @Test
    void execute_shouldNotThrowWhenPeriodIs1Day() {
        LocalDate singleDay = from.plusDays(1);

        var result = useCase.execute(from, singleDay, principal);

        assertThat(result).isNotNull();
    }
}
