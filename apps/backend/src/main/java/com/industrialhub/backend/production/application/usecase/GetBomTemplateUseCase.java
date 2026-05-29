package com.industrialhub.backend.production.application.usecase;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * ADR-044 Decisão 4 — gera template Excel para importação de BOM.
 * Gerado inline via Apache POI (sem nova dependência — POI já disponível).
 */
@Service
public class GetBomTemplateUseCase {

    public byte[] execute() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("BOM");

            // Header row
            Row header = sheet.createRow(0);
            String[] columns = {"parent_code", "component_code", "quantity", "unit"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            // Example row 1
            Row ex1 = sheet.createRow(1);
            ex1.createCell(0).setCellValue("PROD-001");
            ex1.createCell(1).setCellValue("COMP-101");
            ex1.createCell(2).setCellValue(2.0);
            ex1.createCell(3).setCellValue("UN");

            // Example row 2
            Row ex2 = sheet.createRow(2);
            ex2.createCell(0).setCellValue("PROD-001");
            ex2.createCell(1).setCellValue("RAW-201");
            ex2.createCell(2).setCellValue(0.5);
            ex2.createCell(3).setCellValue("KG");

            // Auto-size columns
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar template BOM", e);
        }
    }
}
