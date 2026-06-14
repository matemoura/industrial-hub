package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.UserDataExportResponse;
import com.industrialhub.backend.common.application.service.AbstractMsbPdfRenderer;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Gera o PDF de exportação de dados pessoais (LGPD) usando o padrão AbstractMsbPdfRenderer.
 */
@Service
public class ExportUserDataPdfUseCase extends AbstractMsbPdfRenderer {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter D_FMT  = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] execute(UserDataExportResponse data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
        Document doc = new Document(pdf, PageSize.A4);
        doc.setMargins(36, 36, 36, 36);

        addFooterWithPageNumber(pdf, "Exportação de Dados Pessoais (LGPD)");

        String subtitle = "Usuário: " + data.profile().username()
                + "  |  Gerado em: " + data.exportedAt().format(DT_FMT);
        addMsbHeader(doc, "Exportação de Dados Pessoais (LGPD)", subtitle);

        addPerfilSection(doc, data.profile());
        addNcsSection(doc, data.nonConformancesReported());
        addWorkOrdersSection(doc, data.workOrdersOpened());
        addAuditLogSection(doc, data.auditLogEntries());

        doc.close();
        return baos.toByteArray();
    }

    private void addPerfilSection(Document doc, UserDataExportResponse.ProfileSummary profile) {
        addSectionTitle(doc, "Perfil do Usuário");
        addStyledTable(doc,
                new String[]{"Campo", "Valor"},
                List.of(
                        new String[]{"Usuário",  profile.username()},
                        new String[]{"E-mail",   profile.email()},
                        new String[]{"Perfil",   profile.role()},
                        new String[]{"Ativo",    profile.active() ? "Sim" : "Não"}
                ));
        doc.add(new Paragraph(" "));
    }

    private void addNcsSection(Document doc, List<UserDataExportResponse.NcSummaryForExport> ncs) {
        addSectionTitle(doc, "Não-Conformidades Registradas (" + ncs.size() + ")");
        if (ncs.isEmpty()) {
            doc.add(new Paragraph("Nenhum registro encontrado.").setFontSize(9).setItalic());
        } else {
            List<String[]> rows = new ArrayList<>();
            for (var nc : ncs) {
                rows.add(new String[]{
                        nc.title(),
                        nc.type(),
                        nc.severity(),
                        nc.status(),
                        nc.reportedAt() != null ? nc.reportedAt().format(D_FMT) : ""
                });
            }
            addStyledTable(doc, new String[]{"Título", "Tipo", "Severidade", "Status", "Data"}, rows);
        }
        doc.add(new Paragraph(" "));
    }

    private void addWorkOrdersSection(Document doc, List<UserDataExportResponse.WorkOrderSummaryForExport> wos) {
        addSectionTitle(doc, "Ordens de Serviço Abertas (" + wos.size() + ")");
        if (wos.isEmpty()) {
            doc.add(new Paragraph("Nenhum registro encontrado.").setFontSize(9).setItalic());
        } else {
            List<String[]> rows = new ArrayList<>();
            for (var wo : wos) {
                rows.add(new String[]{
                        wo.title(),
                        wo.type(),
                        wo.priority(),
                        wo.status(),
                        wo.openedAt() != null ? wo.openedAt().format(D_FMT) : ""
                });
            }
            addStyledTable(doc, new String[]{"Título", "Tipo", "Prioridade", "Status", "Data"}, rows);
        }
        doc.add(new Paragraph(" "));
    }

    private void addAuditLogSection(Document doc, List<UserDataExportResponse.AuditLogSummaryForExport> logs) {
        addSectionTitle(doc, "Log de Auditoria (" + logs.size() + ")");
        if (logs.isEmpty()) {
            doc.add(new Paragraph("Nenhum registro encontrado.").setFontSize(9).setItalic());
        } else {
            List<String[]> rows = new ArrayList<>();
            for (var log : logs) {
                rows.add(new String[]{
                        log.action(),
                        log.entityType(),
                        log.entityId() != null ? log.entityId() : "",
                        log.timestamp() != null ? log.timestamp().format(DT_FMT) : ""
                });
            }
            addStyledTable(doc, new String[]{"Ação", "Entidade", "ID", "Data/Hora"}, rows);
        }
        doc.add(new Paragraph(" "));
    }
}
