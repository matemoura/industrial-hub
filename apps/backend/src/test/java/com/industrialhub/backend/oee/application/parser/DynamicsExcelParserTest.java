package com.industrialhub.backend.oee.application.parser;

import com.industrialhub.backend.oee.application.usecase.InvalidExcelFormatException;
import com.industrialhub.backend.oee.domain.RecordType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class DynamicsExcelParserTest {

    private DynamicsExcelParser parser;

    @BeforeEach
    void setUp() {
        parser = new DynamicsExcelParser();
    }

    @Test
    void parse_validFile_returnsCorrectResult() throws IOException {
        byte[] xlsx = buildValidExcel();

        ParseResult result = parser.parse(new ByteArrayInputStream(xlsx), "test.xlsx");

        assertThat(result.rows()).hasSize(3);
        assertThat(result.workerCount()).isEqualTo(1);
        assertThat(result.periodDate()).isEqualTo(LocalDate.of(2026, 4, 28));
        assertThat(result.skippedCount()).isEqualTo(0);
    }

    @Test
    void parse_classifiesProcessoAsProductive() throws IOException {
        byte[] xlsx = buildValidExcel();

        ParseResult result = parser.parse(new ByteArrayInputStream(xlsx), "test.xlsx");

        ParsedRow processo = result.rows().stream()
                .filter(r -> r.recordType() == RecordType.PROCESSO)
                .findFirst().orElseThrow();
        assertThat(processo.hours()).isEqualByComparingTo(new BigDecimal("1.5700"));
    }

    @Test
    void parse_classifiesAtividadeIndiretaCorrectly() throws IOException {
        byte[] xlsx = buildValidExcel();

        ParseResult result = parser.parse(new ByteArrayInputStream(xlsx), "test.xlsx");

        long indiretas = result.rows().stream()
                .filter(r -> r.recordType() == RecordType.ATIVIDADE_INDIRETA)
                .count();
        assertThat(indiretas).isEqualTo(1);
    }

    @Test
    void parse_missingRequiredColumn_throwsInvalidExcelFormatException() throws IOException {
        byte[] xlsx = buildExcelMissingColumn();

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(xlsx), "test.xlsx"))
                .isInstanceOf(InvalidExcelFormatException.class)
                .hasMessageContaining("Colunas ausentes");
    }

    @Test
    void parse_unknownRecordType_skipsRow() throws IOException {
        byte[] xlsx = buildExcelWithUnknownType();

        ParseResult result = parser.parse(new ByteArrayInputStream(xlsx), "test.xlsx");

        assertThat(result.skippedCount()).isGreaterThan(0);
    }

    // --- builders de Excel para testes ---

    private byte[] buildValidExcel() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet();
            var header = sheet.createRow(0);
            String[] cols = {"Trabalhador", "Nome", "Data do perfil", "Hora inicial", "Hora final",
                    "Tipo de registro do diário", "Referência", "Nº oper.", "Ident. do trabalho",
                    "Descrição", "Data inicial", "Data final", "Hora", "Erro", "Ident. do trabalho2"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }

            var style = wb.createCellStyle();
            style.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));

            // Registro de entrada
            addRow(sheet, 1, 7, "JANETE", "2026-04-28", "2026-04-29 07:58", "2026-04-29 07:59",
                    "Registro de entrada", "Sistema", 0, "OPTR1", "Registro de entrada", "2026-04-28", "2026-04-28", 0.00);

            // Processo
            addRow(sheet, 2, 7, "JANETE", "2026-04-28", "2026-04-29 08:00", "2026-04-29 09:34",
                    "Processo", "OP26000594", 10, "OPTR2", "Montagem Fibra Laser", "2026-04-28", "2026-04-28", 1.57);

            // Atividade indireta
            addRow(sheet, 3, 7, "JANETE", "2026-04-28", "2026-04-29 09:34", "2026-04-29 10:00",
                    "Atividade indireta", "Sistema", 0, "OPTR3", "Café e Pausas", "2026-04-28", "2026-04-28", 0.36);

            return toBytes(wb);
        }
    }

    private byte[] buildExcelMissingColumn() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet();
            var header = sheet.createRow(0);
            // sem a coluna "Tipo de registro do diário"
            header.createCell(0).setCellValue("Trabalhador");
            header.createCell(1).setCellValue("Nome");
            return toBytes(wb);
        }
    }

    private byte[] buildExcelWithUnknownType() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet();
            var header = sheet.createRow(0);
            String[] cols = {"Trabalhador", "Nome", "Data do perfil", "Hora inicial", "Hora final",
                    "Tipo de registro do diário", "Referência", "Nº oper.", "Ident. do trabalho",
                    "Descrição", "Data inicial", "Data final", "Hora", "Erro", "Ident. do trabalho2"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

            addRow(sheet, 1, 7, "JANETE", "2026-04-28", "2026-04-29 08:00", "2026-04-29 09:00",
                    "TIPO_DESCONHECIDO", "Sistema", 0, "OPTR1", "Desc", "2026-04-28", "2026-04-28", 0.5);

            return toBytes(wb);
        }
    }

    private void addRow(org.apache.poi.ss.usermodel.Sheet sheet, int rowNum,
                        long workerId, String workerName, String profileDate,
                        String startTime, String endTime, String type,
                        String ref, int opNum, String ident, String desc,
                        String dateIni, String dateFin, double hours) {
        var row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(workerId);
        row.createCell(1).setCellValue(workerName);
        setDateCell(sheet, row, 2, LocalDate.parse(profileDate));
        setDateTimeCell(sheet, row, 3, LocalDateTime.parse(startTime.replace(" ", "T")));
        setDateTimeCell(sheet, row, 4, LocalDateTime.parse(endTime.replace(" ", "T")));
        row.createCell(5).setCellValue(type);
        row.createCell(6).setCellValue(ref);
        row.createCell(7).setCellValue(opNum);
        row.createCell(8).setCellValue(ident);
        row.createCell(9).setCellValue(desc);
        setDateCell(sheet, row, 10, LocalDate.parse(dateIni));
        setDateCell(sheet, row, 11, LocalDate.parse(dateFin));
        row.createCell(12).setCellValue(hours);
        row.createCell(13).setCellValue("Não");
        row.createCell(14).setCellValue(ident);
    }

    private void setDateCell(org.apache.poi.ss.usermodel.Sheet sheet,
                             org.apache.poi.ss.usermodel.Row row, int col, LocalDate date) {
        var wb = sheet.getWorkbook();
        var style = wb.createCellStyle();
        style.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));
        var cell = row.createCell(col);
        cell.setCellValue(date.atStartOfDay());
        cell.setCellStyle(style);
    }

    private void setDateTimeCell(org.apache.poi.ss.usermodel.Sheet sheet,
                                 org.apache.poi.ss.usermodel.Row row, int col, LocalDateTime dt) {
        var wb = sheet.getWorkbook();
        var style = wb.createCellStyle();
        style.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
        var cell = row.createCell(col);
        cell.setCellValue(dt);
        cell.setCellStyle(style);
    }

    private byte[] toBytes(XSSFWorkbook wb) throws IOException {
        var out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }
}
