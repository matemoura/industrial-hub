package com.industrialhub.backend.qms;

import com.industrialhub.backend.qms.application.dto.QualityReportRequest;
import com.industrialhub.backend.qms.application.dto.QualityReportRequest.ReportFormat;
import com.industrialhub.backend.qms.application.dto.QualityReportRequest.ReportSection;
import com.industrialhub.backend.qms.application.usecase.GenerateQualityReportUseCase;
import com.industrialhub.backend.qms.application.service.QualityReportDataService;
import com.industrialhub.backend.qms.application.service.QualityReportPdfRenderer;
import com.industrialhub.backend.qms.application.service.QualityReportExcelRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerateQualityReportUseCaseTest {

    @Mock
    private QualityReportDataService dataService;

    @Mock
    private QualityReportPdfRenderer pdfRenderer;

    @Mock
    private QualityReportExcelRenderer excelRenderer;

    private GenerateQualityReportUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GenerateQualityReportUseCase(dataService, pdfRenderer, excelRenderer);
    }

    @Test
    void execute_periodExceeds366Days_throwsIllegalArgumentException() {
        QualityReportRequest req = new QualityReportRequest(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 6, 5),  // > 366 days
                ReportFormat.PDF,
                Set.of(ReportSection.NCS)
        );

        assertThatThrownBy(() -> useCase.execute(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366");
    }

    @Test
    void execute_toBeforeFrom_throwsIllegalArgumentException() {
        QualityReportRequest req = new QualityReportRequest(
                LocalDate.of(2026, 6, 5),
                LocalDate.of(2026, 6, 1),  // to < from
                ReportFormat.PDF,
                Set.of(ReportSection.NCS)
        );

        assertThatThrownBy(() -> useCase.execute(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'to'");
    }

    @Test
    void execute_validPdfRequest_callsPdfRenderer() throws IOException {
        QualityReportRequest req = new QualityReportRequest(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 6, 1),
                ReportFormat.PDF,
                Set.of(ReportSection.NCS)
        );

        when(dataService.collect(any(), any())).thenReturn(null);
        when(pdfRenderer.render(any(), any())).thenReturn(new byte[]{1, 2, 3});

        byte[] result = useCase.execute(req);

        verify(pdfRenderer).render(any(), eq(req));
        verify(excelRenderer, never()).render(any(), any());
    }

    @Test
    void execute_validExcelRequest_callsExcelRenderer() throws IOException {
        QualityReportRequest req = new QualityReportRequest(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 6, 1),
                ReportFormat.EXCEL,
                Set.of(ReportSection.GED, ReportSection.RCA)
        );

        when(dataService.collect(any(), any())).thenReturn(null);
        when(excelRenderer.render(any(), any())).thenReturn(new byte[]{4, 5, 6});

        byte[] result = useCase.execute(req);

        verify(excelRenderer).render(any(), eq(req));
        verify(pdfRenderer, never()).render(any(), any());
    }

    @Test
    void execute_exactly366DayPeriod_passes() throws IOException {
        LocalDate from = LocalDate.of(2025, 6, 5);
        LocalDate to = from.plusDays(366);

        QualityReportRequest req = new QualityReportRequest(
                from, to, ReportFormat.PDF,
                Set.of(ReportSection.NCS)
        );

        when(dataService.collect(any(), any())).thenReturn(null);
        when(pdfRenderer.render(any(), any())).thenReturn(new byte[0]);

        // Should not throw
        useCase.execute(req);
    }

    @Test
    void execute_sameDayPeriod_passes() throws IOException {
        LocalDate date = LocalDate.of(2026, 6, 5);

        QualityReportRequest req = new QualityReportRequest(
                date, date, ReportFormat.EXCEL,
                Set.of(ReportSection.CAPAS)
        );

        when(dataService.collect(any(), any())).thenReturn(null);
        when(excelRenderer.render(any(), any())).thenReturn(new byte[0]);

        // Should not throw
        useCase.execute(req);
    }
}
