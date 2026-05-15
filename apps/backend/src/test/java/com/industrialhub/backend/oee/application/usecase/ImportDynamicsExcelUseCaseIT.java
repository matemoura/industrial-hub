package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.ImportResultDto;
import com.industrialhub.backend.oee.infrastructure.ImportBatchRepository;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class ImportDynamicsExcelUseCaseIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ImportDynamicsExcelUseCase useCase;
    @Autowired ImportBatchRepository batchRepository;
    @Autowired TimeRecordRepository timeRecordRepository;

    @Test
    void execute_validFile_persistsBatchAndRecords() throws Exception {
        MockMultipartFile file = buildMockFile("test_28abr.xlsx", LocalDate.of(2026, 4, 28));

        ImportResultDto result = useCase.execute(file, false, "system");

        assertThat(result.batchId()).isNotNull();
        assertThat(result.periodDate()).isEqualTo(LocalDate.of(2026, 4, 28));
        assertThat(result.workerCount()).isEqualTo(1);
        assertThat(result.recordsImported()).isEqualTo(2);

        assertThat(batchRepository.findByPeriodDate(LocalDate.of(2026, 4, 28))).isPresent();
        assertThat(timeRecordRepository.count()).isGreaterThan(0);
    }

    @Test
    void execute_duplicatePeriod_throwsDuplicateImportException() throws Exception {
        MockMultipartFile file = buildMockFile("test_29abr.xlsx", LocalDate.of(2026, 4, 29));
        useCase.execute(file, false, "system");

        MockMultipartFile duplicate = buildMockFile("test_29abr_dup.xlsx", LocalDate.of(2026, 4, 29));

        assertThatThrownBy(() -> useCase.execute(duplicate, false, "system"))
                .isInstanceOf(DuplicateImportException.class)
                .hasMessageContaining("2026-04-29");
    }

    private MockMultipartFile buildMockFile(String name, LocalDate date) throws Exception {
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet();
            var header = sheet.createRow(0);
            String[] cols = {"Trabalhador", "Nome", "Data do perfil", "Hora inicial", "Hora final",
                    "Tipo de registro do diário", "Referência", "Nº oper.", "Ident. do trabalho",
                    "Descrição", "Data inicial", "Data final", "Hora", "Erro", "Ident. do trabalho2"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

            // Registro de entrada
            var r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue(7);
            r1.createCell(1).setCellValue("JANETE");
            setDate(sheet, r1, 2, date);
            setDateTime(sheet, r1, 3, date.atTime(8, 0));
            setDateTime(sheet, r1, 4, date.atTime(8, 1));
            r1.createCell(5).setCellValue("Registro de entrada");
            r1.createCell(6).setCellValue("Sistema");
            r1.createCell(7).setCellValue(0);
            r1.createCell(8).setCellValue("OPTR1");
            r1.createCell(9).setCellValue("Registro de entrada");
            setDate(sheet, r1, 10, date);
            setDate(sheet, r1, 11, date);
            r1.createCell(12).setCellValue(0.0);
            r1.createCell(13).setCellValue("Não");
            r1.createCell(14).setCellValue("OPTR1");

            // Processo
            var r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue(7);
            r2.createCell(1).setCellValue("JANETE");
            setDate(sheet, r2, 2, date);
            setDateTime(sheet, r2, 3, date.atTime(8, 1));
            setDateTime(sheet, r2, 4, date.atTime(9, 35));
            r2.createCell(5).setCellValue("Processo");
            r2.createCell(6).setCellValue("OP26000594");
            r2.createCell(7).setCellValue(10);
            r2.createCell(8).setCellValue("OPTR2");
            r2.createCell(9).setCellValue("Montagem Fibra Laser");
            setDate(sheet, r2, 10, date);
            setDate(sheet, r2, 11, date);
            r2.createCell(12).setCellValue(1.57);
            r2.createCell(13).setCellValue("Não");
            r2.createCell(14).setCellValue("OPTR2");

            var out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("file", name,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    private void setDate(org.apache.poi.ss.usermodel.Sheet sheet,
                         org.apache.poi.ss.usermodel.Row row, int col, LocalDate date) {
        var style = sheet.getWorkbook().createCellStyle();
        style.setDataFormat(sheet.getWorkbook().createDataFormat().getFormat("yyyy-mm-dd"));
        var cell = row.createCell(col);
        cell.setCellValue(date.atStartOfDay());
        cell.setCellStyle(style);
    }

    private void setDateTime(org.apache.poi.ss.usermodel.Sheet sheet,
                             org.apache.poi.ss.usermodel.Row row, int col, LocalDateTime dt) {
        var style = sheet.getWorkbook().createCellStyle();
        style.setDataFormat(sheet.getWorkbook().createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
        var cell = row.createCell(col);
        cell.setCellValue(dt);
        cell.setCellStyle(style);
    }
}
