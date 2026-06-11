package com.industrialhub.backend.production.application.usecase;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

    /** Returns the first non-null, non-blank value found among the given column name aliases. */
    public static String getStringByAliases(Row row, Map<String, Integer> colIndex, String... aliases) {
        for (String alias : aliases) {
            String v = getString(row, colIndex, alias);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
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
                    yield Double.parseDouble(cell.getStringCellValue().trim().replace(",", "."));
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    /** Returns the first non-null double found among the given column name aliases. */
    public static Double getDoubleByAliases(Row row, Map<String, Integer> colIndex, String... aliases) {
        for (String alias : aliases) {
            Double v = getDouble(row, colIndex, alias);
            if (v != null) return v;
        }
        return null;
    }

    public static Integer getInteger(Row row, Map<String, Integer> colIndex, String colName) {
        Double d = getDouble(row, colIndex, colName);
        return d != null ? d.intValue() : null;
    }

    /** Returns the first non-null integer found among the given column name aliases. */
    public static Integer getIntegerByAliases(Row row, Map<String, Integer> colIndex, String... aliases) {
        for (String alias : aliases) {
            Integer v = getInteger(row, colIndex, alias);
            if (v != null) return v;
        }
        return null;
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
                String s = cell.getStringCellValue().trim();
                for (DateTimeFormatter fmt : new DateTimeFormatter[]{
                        DATE_FMT,
                        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                        DateTimeFormatter.ofPattern("d/M/yyyy"),
                }) {
                    try { yield LocalDate.parse(s, fmt); } catch (DateTimeParseException ignored) {}
                }
                yield null;
            }
            default -> null;
        };
    }

    /** Returns the first non-null date found among the given column name aliases. */
    public static LocalDate getLocalDateByAliases(Row row, Map<String, Integer> colIndex, String... aliases) {
        for (String alias : aliases) {
            LocalDate v = getLocalDate(row, colIndex, alias);
            if (v != null) return v;
        }
        return null;
    }

    /**
     * Checks that all required columns are present in the parsed header index.
     * Returns a descriptive error message when any are missing, or null if all are found.
     */
    public static String validateRequiredColumns(Map<String, Integer> colIndex, String... required) {
        List<String> missing = new ArrayList<>();
        for (String col : required) {
            if (!colIndex.containsKey(col)) {
                missing.add(col);
            }
        }
        if (missing.isEmpty()) return null;
        List<String> detected = new ArrayList<>(colIndex.keySet());
        Collections.sort(detected);
        return "Colunas obrigatórias ausentes: [" + String.join(", ", missing) + "]. "
             + "Cabeçalhos detectados no arquivo: [" + String.join(", ", detected) + "]";
    }

    /**
     * Validates that for each field (represented as a group of aliases), at least one alias
     * is present in the column index. Reports by the first alias name when all aliases are missing.
     */
    public static String validateRequiredColumnsAliased(Map<String, Integer> colIndex, String[]... fieldAliases) {
        List<String> missing = new ArrayList<>();
        for (String[] aliases : fieldAliases) {
            boolean found = false;
            for (String alias : aliases) {
                if (colIndex.containsKey(alias)) { found = true; break; }
            }
            if (!found) missing.add(aliases[0]);
        }
        if (missing.isEmpty()) return null;
        List<String> detected = new ArrayList<>(colIndex.keySet());
        Collections.sort(detected);
        return "Colunas obrigatórias ausentes: [" + String.join(", ", missing) + "]. "
             + "Cabeçalhos detectados no arquivo: [" + String.join(", ", detected) + "]";
    }
}
