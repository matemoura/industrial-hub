package com.industrialhub.backend.qms.application.service;

import com.industrialhub.backend.common.application.service.AbstractMsbPdfRenderer;
import com.industrialhub.backend.qms.application.dto.CapaAgingResponse.OverdueBySeverity;
import com.industrialhub.backend.qms.application.dto.QualityReportData;
import com.industrialhub.backend.qms.application.dto.QualityReportData.*;
import com.industrialhub.backend.qms.application.dto.QualityReportRequest;
import com.industrialhub.backend.qms.application.dto.QualityReportRequest.ReportSection;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 39 / ADR-050 Decisão 7: renderiza relatório executivo de qualidade em PDF usando iText 7.
 * Estende {@link AbstractMsbPdfRenderer} para reutilizar cabeçalho, tabela estilizada e rodapé MSB.
 */
@Component
public class QualityReportPdfRenderer extends AbstractMsbPdfRenderer {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] render(QualityReportData data, QualityReportRequest req) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
        Document doc = new Document(pdf, PageSize.A4);
        doc.setMargins(36, 36, 36, 36);

        addFooterWithPageNumber(pdf, "Relatório Executivo de Qualidade");

        String subtitle = "Período: " + data.from().format(DATE_FMT) + " a " + data.to().format(DATE_FMT)
                + "  |  Gerado em: " + LocalDate.now().format(DATE_FMT);
        addMsbHeader(doc, "MSB — Relatório Executivo de Qualidade", subtitle);

        if (req.sections().contains(ReportSection.NCS) && data.ncSummary() != null) {
            addNcSummarySection(doc, data.ncSummary());
        }

        if (req.sections().contains(ReportSection.CAPAS) && data.capaStatus() != null) {
            addCapaStatusSection(doc, data.capaStatus());
        }

        if (req.sections().contains(ReportSection.GED) && data.aging() != null) {
            addAgingSection(doc, data.aging());
        }

        if (req.sections().contains(ReportSection.RCA) && data.gedMetrics() != null) {
            addGedMetricsSection(doc, data.gedMetrics());
        }

        doc.close();
        return baos.toByteArray();
    }

    private void addNcSummarySection(Document doc, NcSummaryData summary) {
        addSectionTitle(doc, "Não-Conformidades");

        doc.add(new Paragraph("Total: " + summary.total()).setFontSize(11));

        if (!summary.bySeverity().isEmpty()) {
            doc.add(new Paragraph("Por Severidade:").setFontSize(10).setBold());
            List<String[]> rows = new ArrayList<>();
            for (NcBySeverityRow row : summary.bySeverity()) {
                rows.add(new String[]{row.severity(), str(row.count())});
            }
            addStyledTable(doc, new String[]{"Severidade", "Qtd"}, rows);
        }

        if (!summary.byStatus().isEmpty()) {
            doc.add(new Paragraph("Por Status:").setFontSize(10).setBold());
            List<String[]> rows = new ArrayList<>();
            for (NcByStatusRow row : summary.byStatus()) {
                rows.add(new String[]{row.status(), str(row.count())});
            }
            addStyledTable(doc, new String[]{"Status", "Qtd"}, rows);
        }

        doc.add(new Paragraph(" "));
    }

    private void addCapaStatusSection(Document doc, CapaStatusData capaStatus) {
        addSectionTitle(doc, "CAPAs — Status");

        doc.add(new Paragraph("Total: " + capaStatus.total()).setFontSize(11));

        if (!capaStatus.byStatus().isEmpty()) {
            List<String[]> rows = new ArrayList<>();
            capaStatus.byStatus().forEach((k, v) -> rows.add(new String[]{k, str(v)}));
            addStyledTable(doc, new String[]{"Status", "Qtd"}, rows);
        }

        doc.add(new Paragraph(" "));
    }

    private void addAgingSection(Document doc, com.industrialhub.backend.qms.application.dto.CapaAgingResponse aging) {
        addSectionTitle(doc, "Aging de CAPAs");

        List<String[]> rows = List.of(
                new String[]{"Total em aberto", str(aging.totalOpen())},
                new String[]{"Vencidas",        str(aging.overdueCount())},
                new String[]{"Sem prazo",        str(aging.noDueDateCount())},
                new String[]{"0–7 dias",         str(aging.bucket0to7().count())},
                new String[]{"8–15 dias",        str(aging.bucket8to15().count())},
                new String[]{"16–30 dias",       str(aging.bucket16to30().count())},
                new String[]{">30 dias",         str(aging.bucketOver30().count())}
        );
        addStyledTable(doc, new String[]{"Indicador", "Qtd"}, rows);

        if (!aging.overdueByNcSeverity().isEmpty()) {
            doc.add(new Paragraph("Vencidas por Severidade da NC:").setFontSize(10).setBold());
            List<String[]> sevRows = new ArrayList<>();
            for (OverdueBySeverity row : aging.overdueByNcSeverity()) {
                sevRows.add(new String[]{row.severity(), str(row.overdueCount())});
            }
            addStyledTable(doc, new String[]{"Severidade", "Vencidas"}, sevRows);
        }

        doc.add(new Paragraph(" "));
    }

    private void addGedMetricsSection(Document doc, GedMetricsData ged) {
        addSectionTitle(doc, "GED — Documentos Controlados");

        doc.add(new Paragraph("Total de documentos: " + ged.totalDocuments()).setFontSize(11));

        if (!ged.byCategory().isEmpty()) {
            doc.add(new Paragraph("Por Categoria:").setFontSize(10).setBold());
            List<String[]> rows = new ArrayList<>();
            for (DocByCategoryRow row : ged.byCategory()) {
                rows.add(new String[]{row.category(), str(row.count())});
            }
            addStyledTable(doc, new String[]{"Categoria", "Qtd"}, rows);
        }

        if (!ged.byStatus().isEmpty()) {
            doc.add(new Paragraph("Por Status:").setFontSize(10).setBold());
            List<String[]> rows = new ArrayList<>();
            for (DocByStatusRow row : ged.byStatus()) {
                rows.add(new String[]{row.status(), str(row.count())});
            }
            addStyledTable(doc, new String[]{"Status", "Qtd"}, rows);
        }

        doc.add(new Paragraph(" "));
    }

    private String str(long value) {
        return String.valueOf(value);
    }
}
