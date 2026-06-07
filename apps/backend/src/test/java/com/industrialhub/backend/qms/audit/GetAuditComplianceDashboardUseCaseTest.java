package com.industrialhub.backend.qms.audit;

import com.industrialhub.backend.qms.audit.application.dto.AuditComplianceDashboard;
import com.industrialhub.backend.qms.audit.application.usecase.GetAuditComplianceDashboardUseCase;
import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.FindingType;
import com.industrialhub.backend.qms.audit.infrastructure.AuditChecklistItemRepository;
import com.industrialhub.backend.qms.audit.infrastructure.AuditFindingRepository;
import com.industrialhub.backend.qms.audit.infrastructure.InternalAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAuditComplianceDashboardUseCaseTest {

    @Mock private InternalAuditRepository auditRepository;
    @Mock private AuditChecklistItemRepository checklistRepository;
    @Mock private AuditFindingRepository findingRepository;

    private GetAuditComplianceDashboardUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetAuditComplianceDashboardUseCase(
            auditRepository, checklistRepository, findingRepository);
    }

    @Test
    void shouldCalculateConformityRate_correctly() {
        // AC US-125 (d): 8 CONFORMING de 10 respondidos = 80.0%
        int year = LocalDate.now().getYear();
        when(auditRepository.countByStatusAndYear(any(), eq(year))).thenReturn(0L);
        when(auditRepository.countOverdue(any())).thenReturn(0L);
        when(findingRepository.countOpenNonConformanceFindings()).thenReturn(0L);
        when(findingRepository.countByType(any())).thenReturn(0L);
        when(checklistRepository.countRespondedSince(any())).thenReturn(10L);
        when(checklistRepository.countConformingSince(any())).thenReturn(8L);

        AuditComplianceDashboard dashboard = useCase.execute();

        assertThat(dashboard.conformityRate()).isEqualTo(80.0);
    }

    @Test
    void shouldCountPlannedAuditWithPastDate_inOverdueAudits() {
        // AC US-125 (e): PLANNED com plannedDate < today → overdueAudits
        int year = LocalDate.now().getYear();
        when(auditRepository.countByStatusAndYear(any(), eq(year))).thenReturn(0L);
        when(auditRepository.countOverdue(eq(LocalDate.now()))).thenReturn(3L);
        when(findingRepository.countOpenNonConformanceFindings()).thenReturn(0L);
        when(findingRepository.countByType(any())).thenReturn(0L);
        when(checklistRepository.countRespondedSince(any())).thenReturn(0L);
        when(checklistRepository.countConformingSince(any())).thenReturn(0L);

        AuditComplianceDashboard dashboard = useCase.execute();

        assertThat(dashboard.overdueAudits()).isEqualTo(3L);
    }

    @Test
    void shouldReturnZeroConformityRate_whenNoRespondedItems() {
        int year = LocalDate.now().getYear();
        when(auditRepository.countByStatusAndYear(any(), eq(year))).thenReturn(0L);
        when(auditRepository.countOverdue(any())).thenReturn(0L);
        when(findingRepository.countOpenNonConformanceFindings()).thenReturn(0L);
        when(findingRepository.countByType(any())).thenReturn(0L);
        when(checklistRepository.countRespondedSince(any())).thenReturn(0L);
        when(checklistRepository.countConformingSince(any())).thenReturn(0L);

        AuditComplianceDashboard dashboard = useCase.execute();

        assertThat(dashboard.conformityRate()).isEqualTo(0.0);
    }
}
