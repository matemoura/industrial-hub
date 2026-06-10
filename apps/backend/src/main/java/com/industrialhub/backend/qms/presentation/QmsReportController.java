package com.industrialhub.backend.qms.presentation;

import com.industrialhub.backend.qms.application.dto.QualityReportRequest;
import com.industrialhub.backend.qms.application.dto.QualityReportRequest.ReportFormat;
import com.industrialhub.backend.qms.application.usecase.GenerateQualityReportUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * Sprint 39 / ADR-050 Decisão 9: controller dedicado para relatórios executivos de qualidade.
 * Separado de QmsController (NCs), CapaController (CAPAs) e GedController por SRP.
 * Base: /api/v1/qms/reports
 */
@RestController
@RequestMapping("/api/v1/qms/reports")
public class QmsReportController {

    private final GenerateQualityReportUseCase generateQualityReport;

    public QmsReportController(GenerateQualityReportUseCase generateQualityReport) {
        this.generateQualityReport = generateQualityReport;
    }

    /**
     * Gera relatório executivo de qualidade em formato PDF ou Excel.
     * Período máximo: 366 dias. Seções selecionáveis: NCS, CAPAS, GED, RCA.
     */
    @PostMapping("/quality")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ResponseEntity<byte[]> generateQualityReport(
            @Valid @RequestBody QualityReportRequest req) throws IOException {

        byte[] bytes = generateQualityReport.execute(req);

        boolean isPdf = req.format() == ReportFormat.PDF;
        String contentType = isPdf
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        String extension = isPdf ? "pdf" : "xlsx";
        String filename = String.format("relatorio-qualidade-%s-%s.%s", req.from(), req.to(), extension);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }
}
