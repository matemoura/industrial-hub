package com.industrialhub.backend.oee.application.parser;

import com.industrialhub.backend.oee.application.usecase.InvalidExcelFormatException;
import com.industrialhub.backend.oee.domain.RecordType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class DynamicsExcelParser {

    private static final Logger log = LoggerFactory.getLogger(DynamicsExcelParser.class);

    private static final List<String> REQUIRED_COLUMNS = List.of(
            "Trabalhador", "Nome", "Data do perfil",
            "Hora inicial", "Hora final", "Tipo de registro do diário",
            "Referência", "Descrição", "Data inicial", "Hora"
    );

    public ParseResult parse(InputStream inputStream, String fileName) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);

            Map<String, Integer> colIdx = buildColumnIndex(sheet.getRow(0));
            validateRequiredColumns(colIdx);

            List<ParsedRow> rows = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            List<LocalDate> allDates = new ArrayList<>();
            int skipped = 0;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;

                // Coleta a data de qualquer linha antes de filtrar pelo tipo
                LocalDate rowDate = readLocalDate(row, colIdx, "Data do perfil");
                if (rowDate != null) allDates.add(rowDate);

                try {
                    ParsedRow parsed = parseRow(row, colIdx);
                    if (parsed == null) {
                        skipped++;
                    } else {
                        rows.add(parsed);
                    }
                } catch (Exception e) {
                    errors.add("Linha " + (i + 1) + ": " + e.getMessage());
                    skipped++;
                }
            }

            LocalDate periodDate = allDates.stream()
                    .min(Comparator.naturalOrder())
                    .orElseThrow(() -> new InvalidExcelFormatException("Nenhuma data válida encontrada no arquivo"));

            long workerCount = rows.stream()
                    .map(ParsedRow::workerId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();

            return new ParseResult(periodDate, (int) workerCount, rows, skipped, errors);

        } catch (IOException e) {
            throw new InvalidExcelFormatException("Não foi possível ler o arquivo: " + e.getMessage());
        }
    }

    private Map<String, Integer> buildColumnIndex(Row headerRow) {
        if (headerRow == null) {
            throw new InvalidExcelFormatException("Arquivo sem cabeçalho");
        }
        Map<String, Integer> index = new HashMap<>();
        for (Cell cell : headerRow) {
            String header = readStringCell(cell);
            if (header != null) {
                index.put(header.trim(), cell.getColumnIndex());
            }
        }
        return index;
    }

    private void validateRequiredColumns(Map<String, Integer> colIdx) {
        List<String> missing = REQUIRED_COLUMNS.stream()
                .filter(col -> !colIdx.containsKey(col))
                .toList();
        if (!missing.isEmpty()) {
            throw new InvalidExcelFormatException("Colunas ausentes: " + String.join(", ", missing));
        }
    }

    private ParsedRow parseRow(Row row, Map<String, Integer> col) {
        String typeLabel = readString(row, col, "Tipo de registro do diário");
        RecordType recordType = RecordType.fromDynamicsLabel(typeLabel);

        if (recordType == null) {
            log.warn("Tipo de registro desconhecido na linha {}: '{}'", row.getRowNum() + 1, typeLabel);
            return null;
        }

        Long workerId = readLong(row, col, "Trabalhador");
        String workerName = readString(row, col, "Nome");
        LocalDate profileDate = readLocalDate(row, col, "Data do perfil");

        if (workerId == null || workerId == 0 || workerName == null || profileDate == null) {
            throw new IllegalArgumentException("Campos obrigatórios ausentes: Trabalhador, Nome ou Data do perfil");
        }

        return new ParsedRow(
                workerId,
                workerName,
                profileDate,
                readLocalDateTime(row, col, "Hora inicial"),
                readLocalDateTime(row, col, "Hora final"),
                recordType,
                readString(row, col, "Referência"),
                readInteger(row, col, "Nº oper."),
                readString(row, col, "Ident. do trabalho"),
                readString(row, col, "Descrição"),
                readDecimal(row, col, "Hora")
        );
    }

    // --- helpers de leitura de célula ---

    private Long readLong(Row row, Map<String, Integer> col, String colName) {
        Integer idx = col.get(colName);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> (long) cell.getNumericCellValue();
            case STRING -> {
                try { yield Long.parseLong(cell.getStringCellValue().trim()); }
                catch (NumberFormatException e) { yield null; }
            }
            default -> null;
        };
    }

    private Integer readInteger(Row row, Map<String, Integer> col, String colName) {
        Long val = readLong(row, col, colName);
        return val != null ? val.intValue() : null;
    }

    private String readString(Row row, Map<String, Integer> col, String colName) {
        Integer idx = col.get(colName);
        if (idx == null) return null;
        return readStringCell(row.getCell(idx));
    }

    private String readStringCell(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> {
                String val = cell.getStringCellValue().trim();
                yield val.isEmpty() ? null : val;
            }
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private LocalDate readLocalDate(Row row, Map<String, Integer> col, String colName) {
        LocalDateTime ldt = readLocalDateTime(row, col, colName);
        return ldt != null ? ldt.toLocalDate() : null;
    }

    private LocalDateTime readLocalDateTime(Row row, Map<String, Integer> col, String colName) {
        Integer idx = col.get(colName);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }
        return null;
    }

    private BigDecimal readDecimal(Row row, Map<String, Integer> col, String colName) {
        Integer idx = col.get(colName);
        if (idx == null) return BigDecimal.ZERO;
        Cell cell = row.getCell(idx);
        if (cell == null) return BigDecimal.ZERO;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }
}
