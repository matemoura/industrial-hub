package com.industrialhub.backend.production.application.usecase;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared helper for parsing Excel rows in production import use cases.
 */
public class ExcelParsingHelper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private ExcelParsingHelper() {}

    public static Map<String, Integer> buildColumnIndex(Row header) {
        Map<String, Integer> index = new HashMap<>();
        for (int c = 0; c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            if (cell != null) {
                String key = cell.getStringCellValue().trim().toLowerCase().replace(" ", "_");
                index.put(key, c);
            }
        }
        return index;
    }

    public static String getString(Row row, Map<String, Integer> colIndex, String colName) {
        Integer idx = colIndex.get(colName);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> {
                String v = cell.getStringCellValue().trim();
                yield v.isEmpty() ? null : v;
            }
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    public static Double getDouble(Row row, Map<String, Integer> colIndex, String colName) {
        Integer idx = colIndex.get(colName);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Double.parseDouble(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    public static Integer getInteger(Row row, Map<String, Integer> colIndex, String colName) {
        Double d = getDouble(row, colIndex, colName);
        return d != null ? d.intValue() : null;
    }

    public static LocalDate getLocalDate(Row row, Map<String, Integer> colIndex, String colName) {
        Integer idx = colIndex.get(colName);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> {
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate();
                }
                yield null;
            }
            case STRING -> {
                try {
                    yield LocalDate.parse(cell.getStringCellValue().trim(), DATE_FMT);
                } catch (DateTimeParseException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }
}
