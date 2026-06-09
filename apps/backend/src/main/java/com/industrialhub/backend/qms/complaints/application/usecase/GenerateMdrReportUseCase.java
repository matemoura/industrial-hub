package com.industrialhub.backend.qms.complaints.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.complaints.domain.ComplaintStatus;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaint;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaintNotFoundException;
import com.industrialhub.backend.qms.complaints.domain.InvalidComplaintStatusTransitionException;
import com.industrialhub.backend.qms.complaints.infrastructure.CustomerComplaintRepository;
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
import java.util.UUID;

@Service
public class GenerateMdrReportUseCase {

    private static final DeviceRgb MSB_BLUE = new DeviceRgb(0x1F, 0x3A, 0x4A);
    private static final DeviceRgb MSB_LIGHT = new DeviceRgb(0x56, 0xA4, 0xBB);

    private final CustomerComplaintRepository complaintRepository;
    private final AuditService auditService;

    public GenerateMdrReportUseCase(CustomerComplaintRepository complaintRepository,
                                     AuditService auditService) {
        this.complaintRepository = complaintRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public byte[] execute(UUID id, String principal) {
        CustomerComplaint complaint = complaintRepository.findById(id)
            .orElseThrow(() -> new CustomerComplaintNotFoundException(id));

        if (!complaint.isReportedToAnvisa()) {
            throw new InvalidComplaintStatusTransitionException(
                "Relatório MDR disponível apenas para reclamações reportadas à ANVISA");
        }
        if (complaint.getStatus() != ComplaintStatus.CLOSED) {
            throw new InvalidComplaintStatusTransitionException(
                "Relatório MDR disponível apenas para reclamações CLOSED");
        }

        try {
            byte[] pdf = buildPdf(complaint);
            auditService.log(principal, AuditAction.MDR_REPORT_GENERATED, "CustomerComplaint", id, null);
            return pdf;
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar PDF MDR", e);
        }
    }

    private byte[] buildPdf(CustomerComplaint c) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document doc = new Document(pdfDoc);

        pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new FooterHandler());

        PdfFont bold = PdfFontFactory.createFont(
            com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(
            com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

        // Cabeçalho
        doc.add(new Paragraph("NOTIFICAÇÃO ADVERSA — ANVISA MDR")
            .setFont(bold).setFontSize(16).setFontColor(MSB_BLUE)
            .setTextAlignment(TextAlignment.CENTER).setMarginBottom(4));
        doc.add(new Paragraph("Industrial Hub — MSB Medical System do Brasil")
            .setFont(regular).setFontSize(10).setFontColor(MSB_LIGHT)
            .setTextAlignment(TextAlignment.CENTER).setMarginBottom(20));

        // Seção Identificação
        addSection(doc, bold, "IDENTIFICAÇÃO");
        addField(doc, bold, regular, "Código", c.getCode());
        addField(doc, bold, regular, "Título", c.getTitle());
        addField(doc, bold, regular, "Produto", nvl(c.getProductCode()));
        addField(doc, bold, regular, "Lote", nvl(c.getBatchNumber()));
        addField(doc, bold, regular, "Severidade", c.getSeverity().name());
        addField(doc, bold, regular, "Data Reportada", c.getReportedDate().toString());
        addField(doc, bold, regular, "Reportado Por", c.getReportedBy());
        doc.add(new Paragraph("\n"));

        // Seção Descrição
        addSection(doc, bold, "DESCRIÇÃO");
        doc.add(new Paragraph(nvl(c.getDescription())).setFont(regular).setFontSize(10).setMarginBottom(10));

        // Seção Investigação
        addSection(doc, bold, "INVESTIGAÇÃO");
        addField(doc, bold, regular, "Resumo da Investigação", nvl(c.getInvestigationSummary()));
        addField(doc, bold, regular, "Causa Raiz", nvl(c.getRootCause()));
        addField(doc, bold, regular, "Ação Corretiva", nvl(c.getCorrectiveAction()));
        doc.add(new Paragraph("\n"));

        // Seção Notificação ANVISA
        addSection(doc, bold, "DADOS DA NOTIFICAÇÃO ANVISA");
        addField(doc, bold, regular, "Número do Relatório", nvl(c.getAnvisaReportNumber()));
        addField(doc, bold, regular, "Data da Notificação",
            c.getAnvisaReportDate() != null ? c.getAnvisaReportDate().toString() : "-");

        doc.close();
        return baos.toByteArray();
    }

    private void addSection(Document doc, PdfFont bold, String title) {
        doc.add(new Paragraph(title)
            .setFont(bold).setFontSize(12).setFontColor(MSB_BLUE).setMarginBottom(6));
    }

    private void addField(Document doc, PdfFont bold, PdfFont regular, String label, String value) {
        doc.add(new Paragraph()
            .add(new com.itextpdf.layout.element.Text(label + ": ").setFont(bold).setFontSize(10))
            .add(new com.itextpdf.layout.element.Text(value).setFont(regular).setFontSize(10))
            .setMarginBottom(2));
    }

    private String nvl(String s) {
        return s != null ? s : "-";
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
                String footer = "Industrial Hub — MSB — Confidencial";
                canvas.beginText()
                    .setFontAndSize(font, 8)
                    .moveText(pageSize.getWidth() / 2 - 80, 20)
                    .showText(footer)
                    .endText();
            } catch (IOException e) {
                // ignore footer error
            } finally {
                canvas.release();
            }
        }
    }
}
