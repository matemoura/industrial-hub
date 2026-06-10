package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.ManagementReviewData;
import com.industrialhub.backend.common.domain.AuditAction;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

@Service
public class GenerateManagementReviewPdfUseCase {

    private static final DeviceRgb MSB_BLUE  = new DeviceRgb(0x1F, 0x3A, 0x4A);
    private static final DeviceRgb MSB_LIGHT = new DeviceRgb(0x56, 0xA4, 0xBB);
    private static final DeviceRgb GRAY      = new DeviceRgb(0x81, 0x82, 0x86);

    private final GetManagementReviewDataUseCase getDataUseCase;
    private final AuditService auditService;

    public GenerateManagementReviewPdfUseCase(
        GetManagementReviewDataUseCase getDataUseCase,
        AuditService auditService
    ) {
        this.getDataUseCase = getDataUseCase;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public byte[] execute(LocalDate from, LocalDate to, String principal) {
        ManagementReviewData data = getDataUseCase.execute(from, to, principal);
        try {
            byte[] pdf = buildPdf(from, to, principal, data);
            auditService.log(principal, AuditAction.MANAGEMENT_REVIEW_GENERATED,
                "ManagementReview", (String) null, null);
            return pdf;
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar PDF de Análise Crítica", e);
        }
    }

    private byte[] buildPdf(LocalDate from, LocalDate to, String principal,
                            ManagementReviewData data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document doc = new Document(pdfDoc);

        pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new FooterHandler());

        PdfFont bold    = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

        // Capa
        doc.add(new Paragraph("Análise Crítica pela Direção")
            .setFont(bold).setFontSize(18).setFontColor(MSB_BLUE)
            .setTextAlignment(TextAlignment.CENTER).setMarginBottom(4));
        doc.add(new Paragraph("ISO 13485 §5.6 — Industrial Hub MSB Medical System do Brasil")
            .setFont(regular).setFontSize(10).setFontColor(MSB_LIGHT)
            .setTextAlignment(TextAlignment.CENTER).setMarginBottom(6));
        doc.add(new Paragraph("Período: " + from + " a " + to)
            .setFont(regular).setFontSize(10).setFontColor(GRAY)
            .setTextAlignment(TextAlignment.CENTER).setMarginBottom(4));
        doc.add(new Paragraph("Gerado por: " + principal + " em " + LocalDate.now())
            .setFont(regular).setFontSize(9).setFontColor(GRAY)
            .setTextAlignment(TextAlignment.CENTER).setMarginBottom(20));

        // Seção 1 — Não Conformidades
        addSection(doc, bold, "1. Não Conformidades");
        ManagementReviewData.NcSummary nc = data.ncSummary();
        addRow(doc, bold, regular, "Total reportadas no período", String.valueOf(nc.totalReported()));
        addRow(doc, bold, regular, "NCs críticas abertas",
            nc.criticalOpen() + "  " + semaphoreNc(nc.criticalOpen()));
        addRow(doc, bold, regular, "Tempo médio de resolução (dias)",
            nc.avgResolutionDays() != null ? String.format("%.1f", nc.avgResolutionDays()) : "-");

        // Seção 2 — CAPAs
        addSection(doc, bold, "2. Ações Corretivas e Preventivas (CAPAs)");
        ManagementReviewData.CapaSummary capa = data.capaSummary();
        addRow(doc, bold, regular, "CAPAs abertas", String.valueOf(capa.totalOpen()));
        addRow(doc, bold, regular, "CAPAs vencidas",
            capa.overdueCount() + "  " + semaphoreCapa(capa.overdueCount()));
        addRow(doc, bold, regular, "Taxa de efetividade",
            capa.effectivenessRate() != null ? String.format("%.1f%%", capa.effectivenessRate()) : "-");

        // Seção 3 — Calibrações
        addSection(doc, bold, "3. Calibrações");
        ManagementReviewData.CalibrationSummary cal = data.calibrationSummary();
        addRow(doc, bold, regular, "Calibrações vencidas",
            cal.overdueSchedules() + "  " + semaphoreCalib(cal.overdueSchedules()));
        addRow(doc, bold, regular, "Resultados fora de tolerância (período)", String.valueOf(cal.outOfToleranceCount()));
        addRow(doc, bold, regular, "Taxa de conformidade",
            String.format("%.1f%%", cal.complianceRate() != null ? cal.complianceRate() : 100.0));

        // Seção 4 — Treinamentos
        addSection(doc, bold, "4. Treinamentos e Competências");
        ManagementReviewData.TrainingSummary tr = data.trainingSummary();
        int totalCompetencies = tr.fullyCompliant() + tr.partiallyCompliant() + tr.nonCompliant();
        double compliantPct = totalCompetencies == 0 ? 100.0
            : (tr.fullyCompliant() * 100.0) / totalCompetencies;
        addRow(doc, bold, regular, "Competências válidas",
            tr.fullyCompliant() + "  " + semaphoreTraining(compliantPct));
        addRow(doc, bold, regular, "Expirando (próximos 30 dias)", String.valueOf(tr.expiringIn30Days()));
        addRow(doc, bold, regular, "Expiradas / Ausentes", String.valueOf(tr.nonCompliant()));

        // Seção 5 — Riscos
        addSection(doc, bold, "5. Gestão de Riscos (FMEA)");
        ManagementReviewData.RiskSummary risk = data.riskSummary();
        addRow(doc, bold, regular, "Total de itens de risco", String.valueOf(risk.totalRisks()));
        addRow(doc, bold, regular, "Riscos críticos abertos",
            risk.criticalOpen() + "  " + semaphoreRisk(risk.criticalOpen()));
        addRow(doc, bold, regular, "Mitigados no período", String.valueOf(risk.mitigatedInPeriod()));
        addRow(doc, bold, regular, "RPN médio",
            risk.avgRpn() != null ? String.format("%.1f", risk.avgRpn()) : "-");

        // Seção 6 — OEE / KPI
        addSection(doc, bold, "6. KPIs de Produção");
        ManagementReviewData.KpiSnapshot kpi = data.kpiSummary();
        addRow(doc, bold, regular, "OEE médio (últimos 30 dias)",
            kpi.oee30Days() != null ? String.format("%.1f%%  %s", kpi.oee30Days(), semaphoreOee(kpi.oee30Days())) : "-");
        addRow(doc, bold, regular, "NCs abertas (total)", String.valueOf(kpi.openNcs()));
        addRow(doc, bold, regular, "Ordens de serviço abertas", String.valueOf(kpi.openWorkOrders()));

        doc.close();
        return baos.toByteArray();
    }

    private void addSection(Document doc, PdfFont bold, String title) {
        doc.add(new Paragraph(title)
            .setFont(bold).setFontSize(12).setFontColor(MSB_BLUE)
            .setMarginTop(14).setMarginBottom(6));
    }

    private void addRow(Document doc, PdfFont bold, PdfFont regular, String label, String value) {
        doc.add(new Paragraph()
            .add(new com.itextpdf.layout.element.Text(label + ": ").setFont(bold).setFontSize(10))
            .add(new com.itextpdf.layout.element.Text(value).setFont(regular).setFontSize(10))
            .setMarginBottom(2));
    }

    private String semaphoreNc(int criticalOpen) {
        if (criticalOpen == 0) return "[VERDE]";
        if (criticalOpen <= 3) return "[AMBAR]";
        return "[VERMELHO]";
    }

    private String semaphoreCapa(int overdueCount) {
        if (overdueCount == 0) return "[VERDE]";
        if (overdueCount <= 5) return "[AMBAR]";
        return "[VERMELHO]";
    }

    private String semaphoreCalib(int overdueSchedules) {
        return overdueSchedules == 0 ? "[VERDE]" : "[VERMELHO]";
    }

    private String semaphoreTraining(double compliantPct) {
        if (compliantPct > 90) return "[VERDE]";
        if (compliantPct >= 75) return "[AMBAR]";
        return "[VERMELHO]";
    }

    private String semaphoreRisk(int criticalOpen) {
        return criticalOpen == 0 ? "[VERDE]" : "[VERMELHO]";
    }

    private String semaphoreOee(double oee) {
        if (oee >= 65) return "[VERDE]";
        if (oee >= 50) return "[AMBAR]";
        return "[VERMELHO]";
    }

    private static class FooterHandler implements IEventHandler {
        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdfDoc = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            Rectangle pageSize = page.getPageSize();
            PdfCanvas canvas = new PdfCanvas(page.newContentStreamAfter(), page.getResources(), pdfDoc);
            try {
                PdfFont font = PdfFontFactory.createFont(
                    com.itextpdf.io.font.constants.StandardFonts.HELVETICA);
                canvas.beginText()
                    .setFontAndSize(font, 8)
                    .moveText(pageSize.getWidth() / 2 - 90, 20)
                    .showText("Industrial Hub — MSB — Confidencial")
                    .endText();
            } catch (IOException e) {
                // ignore footer error
            } finally {
                canvas.release();
            }
        }
    }
}
