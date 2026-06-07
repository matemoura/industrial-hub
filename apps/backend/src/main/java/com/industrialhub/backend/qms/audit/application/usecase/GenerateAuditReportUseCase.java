package com.industrialhub.backend.qms.audit.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.audit.domain.AuditChecklistItem;
import com.industrialhub.backend.qms.audit.domain.AuditFinding;
import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.InternalAudit;
import com.industrialhub.backend.qms.audit.domain.InternalAuditNotFoundException;
import com.industrialhub.backend.qms.audit.domain.InvalidAuditStatusTransitionException;
import com.industrialhub.backend.qms.audit.infrastructure.AuditChecklistItemRepository;
import com.industrialhub.backend.qms.audit.infrastructure.AuditFindingRepository;
import com.industrialhub.backend.qms.audit.infrastructure.InternalAuditRepository;
import com.itextpdf.kernel.colors.ColorConstants;
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
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class GenerateAuditReportUseCase {

    private static final DeviceRgb MSB_BLUE = new DeviceRgb(0x1F, 0x3A, 0x4A);
    private static final DeviceRgb MSB_LIGHT = new DeviceRgb(0x56, 0xA4, 0xBB);

    private final InternalAuditRepository auditRepository;
    private final AuditChecklistItemRepository checklistRepository;
    private final AuditFindingRepository findingRepository;
    private final AuditService auditService;

    public GenerateAuditReportUseCase(InternalAuditRepository auditRepository,
                                       AuditChecklistItemRepository checklistRepository,
                                       AuditFindingRepository findingRepository,
                                       AuditService auditService) {
        this.auditRepository = auditRepository;
        this.checklistRepository = checklistRepository;
        this.findingRepository = findingRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public byte[] execute(UUID auditId, String principal) {
        InternalAudit audit = auditRepository.findById(auditId)
            .orElseThrow(() -> new InternalAuditNotFoundException(auditId));

        if (audit.getStatus() != AuditStatus.COMPLETED) {
            throw new InvalidAuditStatusTransitionException(
                "Relatório disponível apenas para auditorias COMPLETED");
        }

        List<AuditChecklistItem> items = checklistRepository.findByAuditIdOrderByItemOrder(auditId);
        List<AuditFinding> findings = findingRepository.findByAuditId(auditId);

        try {
            byte[] pdf = buildPdf(audit, items, findings);
            auditService.log(principal, AuditAction.AUDIT_REPORT_GENERATED, "InternalAudit", auditId, null);
            return pdf;
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar PDF de auditoria", e);
        }
    }

    private byte[] buildPdf(InternalAudit audit, List<AuditChecklistItem> items,
                              List<AuditFinding> findings) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document doc = new Document(pdfDoc);

        // Page footer handler
        pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new PageNumberHandler());

        PdfFont bold = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

        // ── Capa ─────────────────────────────────────────────────────────────
        doc.add(new Paragraph("RELATÓRIO DE AUDITORIA INTERNA")
            .setFont(bold).setFontSize(18).setFontColor(MSB_BLUE)
            .setTextAlignment(TextAlignment.CENTER).setMarginBottom(4));
        doc.add(new Paragraph("ISO 13485 §8.2.4 — MSB Medical System do Brasil")
            .setFont(regular).setFontSize(10).setFontColor(MSB_LIGHT)
            .setTextAlignment(TextAlignment.CENTER).setMarginBottom(20));

        addField(doc, bold, regular, "Código", audit.getCode());
        addField(doc, bold, regular, "Título", audit.getTitle());
        addField(doc, bold, regular, "Tipo", audit.getAuditType().name());
        addField(doc, bold, regular, "Data Planejada", audit.getPlannedDate().toString());
        if (audit.getCompletedDate() != null) {
            addField(doc, bold, regular, "Data de Conclusão", audit.getCompletedDate().toString());
        }
        addField(doc, bold, regular, "Auditor Líder", audit.getLeadAuditor());
        addField(doc, bold, regular, "Auditados", String.join(", ", audit.getAuditees()));
        addField(doc, bold, regular, "Escopo", audit.getScope());

        doc.add(new Paragraph("\n"));

        // ── Sumário Executivo ─────────────────────────────────────────────────
        doc.add(new Paragraph("SUMÁRIO EXECUTIVO")
            .setFont(bold).setFontSize(13).setFontColor(MSB_BLUE).setMarginBottom(6));

        long conforming = items.stream()
            .filter(i -> i.getResponse() != null &&
                i.getResponse() == com.industrialhub.backend.qms.audit.domain.ChecklistResponse.CONFORMING)
            .count();
        long nonConforming = items.stream()
            .filter(i -> i.getResponse() != null &&
                i.getResponse() == com.industrialhub.backend.qms.audit.domain.ChecklistResponse.NON_CONFORMING)
            .count();
        long observation = items.stream()
            .filter(i -> i.getResponse() != null &&
                i.getResponse() == com.industrialhub.backend.qms.audit.domain.ChecklistResponse.OBSERVATION)
            .count();
        long na = items.stream()
            .filter(i -> i.getResponse() != null &&
                i.getResponse() == com.industrialhub.backend.qms.audit.domain.ChecklistResponse.NOT_APPLICABLE)
            .count();

        Table summary = new Table(UnitValue.createPercentArray(new float[]{3, 1}))
            .setWidth(UnitValue.createPercentValue(60));
        addSummaryRow(summary, bold, regular, "Total itens checklist", String.valueOf(items.size()));
        addSummaryRow(summary, bold, regular, "Conformes", String.valueOf(conforming));
        addSummaryRow(summary, bold, regular, "Não-conformes", String.valueOf(nonConforming));
        addSummaryRow(summary, bold, regular, "Observações", String.valueOf(observation));
        addSummaryRow(summary, bold, regular, "N/A", String.valueOf(na));
        addSummaryRow(summary, bold, regular, "Total achados", String.valueOf(findings.size()));
        doc.add(summary);
        doc.add(new Paragraph("\n"));

        // ── Tabela Checklist ──────────────────────────────────────────────────
        if (!items.isEmpty()) {
            doc.add(new Paragraph("CHECKLIST POR PROCESSO")
                .setFont(bold).setFontSize(13).setFontColor(MSB_BLUE).setMarginBottom(6));

            Table checklistTable = new Table(UnitValue.createPercentArray(new float[]{1.5f, 2, 3, 2, 3}))
                .setWidth(UnitValue.createPercentValue(100));
            addHeaderCell(checklistTable, bold, "Cláusula ISO");
            addHeaderCell(checklistTable, bold, "Processo");
            addHeaderCell(checklistTable, bold, "Questão");
            addHeaderCell(checklistTable, bold, "Resposta");
            addHeaderCell(checklistTable, bold, "Evidência");

            for (AuditChecklistItem item : items) {
                checklistTable.addCell(new Cell().add(new Paragraph(nvl(item.getIsoClause())).setFont(regular).setFontSize(8)));
                checklistTable.addCell(new Cell().add(new Paragraph(nvl(item.getProcess())).setFont(regular).setFontSize(8)));
                checklistTable.addCell(new Cell().add(new Paragraph(nvl(item.getQuestion())).setFont(regular).setFontSize(8)));
                checklistTable.addCell(new Cell().add(new Paragraph(item.getResponse() != null ? item.getResponse().name() : "-").setFont(regular).setFontSize(8)));
                checklistTable.addCell(new Cell().add(new Paragraph(nvl(item.getEvidence())).setFont(regular).setFontSize(8)));
            }
            doc.add(checklistTable);
            doc.add(new Paragraph("\n"));
        }

        // ── Tabela Achados ────────────────────────────────────────────────────
        if (!findings.isEmpty()) {
            doc.add(new Paragraph("ACHADOS")
                .setFont(bold).setFontSize(13).setFontColor(MSB_BLUE).setMarginBottom(6));

            Table findingTable = new Table(UnitValue.createPercentArray(new float[]{2, 1.5f, 1.5f, 4, 2}))
                .setWidth(UnitValue.createPercentValue(100));
            addHeaderCell(findingTable, bold, "Tipo");
            addHeaderCell(findingTable, bold, "Severidade");
            addHeaderCell(findingTable, bold, "Cláusula");
            addHeaderCell(findingTable, bold, "Descrição");
            addHeaderCell(findingTable, bold, "NC Vinculada");

            for (AuditFinding f : findings) {
                findingTable.addCell(new Cell().add(new Paragraph(f.getType().name()).setFont(regular).setFontSize(8)));
                findingTable.addCell(new Cell().add(new Paragraph(f.getSeverity().name()).setFont(regular).setFontSize(8)));
                findingTable.addCell(new Cell().add(new Paragraph(nvl(f.getIsoClause())).setFont(regular).setFontSize(8)));
                findingTable.addCell(new Cell().add(new Paragraph(nvl(f.getDescription())).setFont(regular).setFontSize(8)));
                findingTable.addCell(new Cell().add(new Paragraph(f.getLinkedNcId() != null ? f.getLinkedNcId().toString() : "-").setFont(regular).setFontSize(8)));
            }
            doc.add(findingTable);
        }

        doc.close();
        return baos.toByteArray();
    }

    private void addField(Document doc, PdfFont bold, PdfFont regular, String label, String value) {
        doc.add(new Paragraph()
            .add(new com.itextpdf.layout.element.Text(label + ": ").setFont(bold).setFontSize(10))
            .add(new com.itextpdf.layout.element.Text(value != null ? value : "-").setFont(regular).setFontSize(10))
            .setMarginBottom(2));
    }

    private void addSummaryRow(Table table, PdfFont bold, PdfFont regular, String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label).setFont(regular).setFontSize(9)));
        table.addCell(new Cell().add(new Paragraph(value).setFont(bold).setFontSize(9)));
    }

    private void addHeaderCell(Table table, PdfFont bold, String text) {
        table.addHeaderCell(new Cell()
            .add(new Paragraph(text).setFont(bold).setFontSize(9).setFontColor(ColorConstants.WHITE))
            .setBackgroundColor(MSB_BLUE));
    }

    private String nvl(String s) {
        return s != null ? s : "-";
    }

    private static class PageNumberHandler implements IEventHandler {
        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdfDoc = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            int pageNumber = pdfDoc.getPageNumber(page);
            int totalPages = pdfDoc.getNumberOfPages();
            Rectangle pageSize = page.getPageSize();
            PdfCanvas canvas = new PdfCanvas(page.newContentStreamAfter(), page.getResources(), pdfDoc);
            try {
                PdfFont font = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);
                canvas.beginText()
                    .setFontAndSize(font, 8)
                    .moveText(pageSize.getWidth() / 2 - 20, 20)
                    .showText("Página " + pageNumber + " de " + totalPages)
                    .endText();
            } catch (IOException e) {
                // ignore footer error
            } finally {
                canvas.release();
            }
        }
    }
}
