package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.QualityReportData;
import com.industrialhub.backend.qms.application.dto.QualityReportRequest;
import com.industrialhub.backend.qms.application.dto.QualityReportRequest.ReportFormat;
import com.industrialhub.backend.qms.application.service.QualityReportDataService;
import com.industrialhub.backend.qms.application.service.QualityReportExcelRenderer;
import com.industrialhub.backend.qms.application.service.QualityReportPdfRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.temporal.ChronoUnit;

/**
 * Sprint 39 / ADR-050 Decisão 7: orquestra coleta de dados e renderização do relatório executivo.
 * Valida: (1) from não nulo, (2) to não nulo, (3) período máximo de 366 dias.
 */
@Service
@Transactional(readOnly = true)
public class GenerateQualityReportUseCase {

    private final QualityReportDataService dataService;
    private final QualityReportPdfRenderer pdfRenderer;
    private final QualityReportExcelRenderer excelRenderer;

    public GenerateQualityReportUseCase(QualityReportDataService dataService,
                                        QualityReportPdfRenderer pdfRenderer,
                                        QualityReportExcelRenderer excelRenderer) {
        this.dataService = dataService;
        this.pdfRenderer = pdfRenderer;
        this.excelRenderer = excelRenderer;
    }

    public byte[] execute(QualityReportRequest req) throws IOException {
        validateDateRange(req);

        QualityReportData data = dataService.collect(req.from(), req.to());

        if (req.format() == ReportFormat.PDF) {
            return pdfRenderer.render(data, req);
        } else {
            return excelRenderer.render(data, req);
        }
    }

    private void validateDateRange(QualityReportRequest req) {
        if (req.from() == null) {
            throw new IllegalArgumentException("O campo 'from' é obrigatório.");
        }
        if (req.to() == null) {
            throw new IllegalArgumentException("O campo 'to' é obrigatório.");
        }
        if (req.to().isBefore(req.from())) {
            throw new IllegalArgumentException("A data 'to' deve ser igual ou posterior a 'from'.");
        }
        long days = ChronoUnit.DAYS.between(req.from(), req.to());
        if (days > 366) {
            throw new IllegalArgumentException(
                    "O período máximo permitido é de 366 dias. Período solicitado: " + days + " dias.");
        }
    }
}
