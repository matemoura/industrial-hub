package com.industrialhub.backend.common;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.ManagementReviewData;
import com.industrialhub.backend.common.application.usecase.GenerateManagementReviewPdfUseCase;
import com.industrialhub.backend.common.application.usecase.GetManagementReviewDataUseCase;
import com.industrialhub.backend.common.domain.AuditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateManagementReviewPdfUseCaseTest {

    @Mock GetManagementReviewDataUseCase getDataUseCase;
    @Mock AuditService auditService;

    private GenerateManagementReviewPdfUseCase useCase;

    private final LocalDate from = LocalDate.of(2026, 1, 1);
    private final LocalDate to   = LocalDate.of(2026, 12, 31);
    private final String principal = "admin";

    @BeforeEach
    void setUp() {
        useCase = new GenerateManagementReviewPdfUseCase(getDataUseCase, auditService);

        ManagementReviewData mockData = new ManagementReviewData(
            new ManagementReviewData.NcSummary(5, 1, 3.5,
                Map.of("OPEN", 3, "IN_ANALYSIS", 1, "CLOSED", 1),
                Map.of("LOW", 2, "MEDIUM", 2, "HIGH", 1, "CRITICAL", 0)),
            new ManagementReviewData.CapaSummary(3, 0, 85.0),
            new ManagementReviewData.ComplaintSummary(2, 1, 7.0),
            new ManagementReviewData.AuditSummary(3, 0, 0, 1, 90.0),
            new ManagementReviewData.CalibrationSummary(0, 0, 100.0),
            new ManagementReviewData.TrainingSummary(35, 3, 2, 1),
            new ManagementReviewData.RiskSummary(8, 0, 2, 45.0),
            new ManagementReviewData.ChangeSummary(2, 3, 0, 1, 1),
            new ManagementReviewData.KpiSnapshot(72.5, 3, 4)
        );
        when(getDataUseCase.execute(any(), any(), any())).thenReturn(mockData);
    }

    @Test
    void execute_shouldReturnValidPdfBytes() {
        byte[] bytes = useCase.execute(from, to, principal);

        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void execute_shouldRegisterAuditEvent() {
        useCase.execute(from, to, principal);

        verify(auditService, times(1)).log(
            eq(principal),
            eq(AuditAction.MANAGEMENT_REVIEW_GENERATED),
            eq("ManagementReview"),
            isNull(String.class),
            isNull(Map.class)
        );
    }
}
