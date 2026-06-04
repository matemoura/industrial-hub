package com.industrialhub.backend.qms.application.service;

import com.industrialhub.backend.qms.application.dto.CapaAgingResponse;
import com.industrialhub.backend.qms.application.dto.QualityReportData;
import com.industrialhub.backend.qms.application.dto.QualityReportData.*;
import com.industrialhub.backend.qms.application.dto.QualityReportRequest;
import com.industrialhub.backend.qms.application.dto.QualityReportRequest.ReportSection;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Sprint 39 / ADR-050 Decisão 7: renderiza relatório executivo de qualidade em Excel.
 * Apache POI (já presente no pom.xml — ADR-044).
 * Cores MSB: azulFiltrado #56A4BB, azulProfundo #1F3A4A.
 */
@Component
public class QualityReportExcelRenderer {

    private static final byte[] MSB_BLUE_DARK = {0x1F, 0x3A, 0x4A};  // #1F3A4A
    private static final byte[] MSB_BLUE      = {0x56, (byte)0xA4, (byte)0xBB};  // #56A4BB
    private static final byte[] STATUS_OK     = {0x3F, (byte)0xA6, 0x6A};  // #3FA66A
    private static final byte[] STATUS_DANGER = {(byte)0xD2, 0x4A, 0x4A};  // #D24A4A

    public byte[] render(QualityReportData data, QualityReportRequest req) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            // Resumo
            Sheet summary = wb.createSheet("Resumo");
            fillSummarySheet(summary, data, wb);

            if (req.sections().contains(ReportSection.NCS) && data.ncSummary() != null) {
                fillNcSummarySheet(wb.createSheet("NC Summary"), data.ncSummary(), wb);
            }

            if (req.sections().contains(ReportSection.CAPAS) && data.capaStatus() != null) {
                fillCapaStatusSheet(wb.createSheet("CAPA Status"), data.capaStatus(), wb);
            }

            if (req.sections().contains(ReportSection.GED) && data.aging() != null) {
                fillAgingSheet(wb.createSheet("Aging"), data.aging(), wb);
            }

            if (req.sections().contains(ReportSection.RCA) && data.gedMetrics() != null) {
                fillGedSheet(wb.createSheet("GED"), data.gedMetrics(), wb);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private void fillSummarySheet(Sheet sheet, QualityReportData data, XSSFWorkbook wb) {
        CellStyle titleStyle = headerStyle(wb, MSB_BLUE_DARK);
        Row r0 = sheet.createRow(0);
        createCell(r0, 0, "MSB — Relatório Executivo de Qualidade", titleStyle);

        Row r1 = sheet.createRow(1);
        createCell(r1, 0, "Período", null);
        createCell(r1, 1, data.from() + " a " + data.to(), null);

        if (data.ncSummary() != null) {
            Row r2 = sheet.createRow(3);
            createCell(r2, 0, "Total NCs", null);
            createCell(r2, 1, String.valueOf(data.ncSummary().total()), null);
        }

        if (data.capaStatus() != null) {
            Row r3 = sheet.createRow(4);
            createCell(r3, 0, "Total CAPAs", null);
            createCell(r3, 1, String.valueOf(data.capaStatus().total()), null);
        }

        if (data.gedMetrics() != null) {
            Row r4 = sheet.createRow(5);
            createCell(r4, 0, "Total Documentos", null);
            createCell(r4, 1, String.valueOf(data.gedMetrics().totalDocuments()), null);
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void fillNcSummarySheet(Sheet sheet, NcSummaryData summary, XSSFWorkbook wb) {
        CellStyle hdr = headerStyle(wb, MSB_BLUE_DARK);

        Row header = sheet.createRow(0);
        createCell(header, 0, "Severidade", hdr);
        createCell(header, 1, "Qtd", hdr);

        int rowIdx = 1;
        for (NcBySeverityRow row : summary.bySeverity()) {
            Row r = sheet.createRow(rowIdx++);
            createCell(r, 0, row.severity(), null);
            createCell(r, 1, String.valueOf(row.count()), null);
        }

        rowIdx++;
        Row statusHdr = sheet.createRow(rowIdx++);
        createCell(statusHdr, 0, "Status", hdr);
        createCell(statusHdr, 1, "Qtd", hdr);

        for (NcByStatusRow row : summary.byStatus()) {
            Row r = sheet.createRow(rowIdx++);
            createCell(r, 0, row.status(), null);
            createCell(r, 1, String.valueOf(row.count()), null);
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void fillCapaStatusSheet(Sheet sheet, CapaStatusData capaStatus, XSSFWorkbook wb) {
        CellStyle hdr = headerStyle(wb, MSB_BLUE_DARK);

        Row header = sheet.createRow(0);
        createCell(header, 0, "Status", hdr);
        createCell(header, 1, "Qtd", hdr);

        int rowIdx = 1;
        for (var entry : capaStatus.byStatus().entrySet()) {
            Row r = sheet.createRow(rowIdx++);
            createCell(r, 0, entry.getKey(), null);
            createCell(r, 1, String.valueOf(entry.getValue()), null);
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void fillAgingSheet(Sheet sheet, CapaAgingResponse aging, XSSFWorkbook wb) {
        CellStyle hdr = headerStyle(wb, MSB_BLUE_DARK);

        Row header = sheet.createRow(0);
        createCell(header, 0, "Indicador", hdr);
        createCell(header, 1, "Qtd", hdr);

        int rowIdx = 1;
        addAgingRow(sheet, rowIdx++, "Total em aberto", aging.totalOpen(), null);
        addAgingRow(sheet, rowIdx++, "Vencidas", aging.overdueCount(), aging.overdueCount() > 0 ? STATUS_DANGER : null);
        addAgingRow(sheet, rowIdx++, "Sem prazo", aging.noDueDateCount(), null);
        addAgingRow(sheet, rowIdx++, "0–7 dias", aging.bucket0to7().count(), null);
        addAgingRow(sheet, rowIdx++, "8–15 dias", aging.bucket8to15().count(), null);
        addAgingRow(sheet, rowIdx++, "16–30 dias", aging.bucket16to30().count(), null);
        addAgingRow(sheet, rowIdx++, ">30 dias", aging.bucketOver30().count(), null);

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void addAgingRow(Sheet sheet, int rowIdx, String label, long count, byte[] color) {
        Row r = sheet.createRow(rowIdx);
        createCell(r, 0, label, null);
        createCell(r, 1, String.valueOf(count), null);
    }

    private void fillGedSheet(Sheet sheet, GedMetricsData ged, XSSFWorkbook wb) {
        CellStyle hdr = headerStyle(wb, MSB_BLUE_DARK);

        Row header = sheet.createRow(0);
        createCell(header, 0, "Categoria", hdr);
        createCell(header, 1, "Qtd", hdr);

        int rowIdx = 1;
        for (DocByCategoryRow row : ged.byCategory()) {
            Row r = sheet.createRow(rowIdx++);
            createCell(r, 0, row.category(), null);
            createCell(r, 1, String.valueOf(row.count()), null);
        }

        rowIdx++;
        Row statusHdr = sheet.createRow(rowIdx++);
        createCell(statusHdr, 0, "Status", hdr);
        createCell(statusHdr, 1, "Qtd", hdr);

        for (DocByStatusRow row : ged.byStatus()) {
            Row r = sheet.createRow(rowIdx++);
            createCell(r, 0, row.status(), null);
            createCell(r, 1, String.valueOf(row.count()), null);
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private CellStyle headerStyle(XSSFWorkbook wb, byte[] rgb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(rgb, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        if (style != null) {
            cell.setCellStyle(style);
        }
    }
}
