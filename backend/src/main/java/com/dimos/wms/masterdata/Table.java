package com.dimos.wms.masterdata;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** A spreadsheet as the importer sees it: normalised column names and the data rows. */
record Table(List<String> headers, List<Line> lines) {

    /** Column names are compared without case, spaces, underscores or other punctuation: "Area Code" = area_code. */
    static String norm(String header) {
        return header == null ? "" : header.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /** One data row. {@code number} is the row number as shown in Excel (the header is row 1). */
    record Line(int number, Map<String, String> values) {

        boolean has(String column) {
            return values.containsKey(norm(column));
        }

        /** Trimmed text, or null when the cell is empty or the column is not in the file. */
        String str(String column) {
            String v = values.get(norm(column));
            return v == null || v.isBlank() ? null : v.trim();
        }

        /** The cell if the column is in the file, otherwise {@code existing} (a column left out keeps its value). */
        String str(String column, String existing) {
            return has(column) ? str(column) : existing;
        }

        Integer integer(String column, Integer existing) {
            if (!has(column)) {
                return existing;
            }
            String v = str(column);
            if (v == null) {
                return null;
            }
            try {
                return new BigDecimal(v.replace(",", "")).intValueExact();
            } catch (ArithmeticException | NumberFormatException ex) {
                throw new ImportRowException(column + ": '" + v + "' is not a whole number");
            }
        }

        BigDecimal decimal(String column, BigDecimal existing) {
            if (!has(column)) {
                return existing;
            }
            String v = str(column);
            if (v == null) {
                return null;
            }
            try {
                return new BigDecimal(v.replace(",", ""));
            } catch (NumberFormatException ex) {
                throw new ImportRowException(column + ": '" + v + "' is not a number");
            }
        }

        /** TRUE/FALSE, YES/NO, Y/N, 1/0. Empty = {@code existing}. */
        Boolean bool(String column, Boolean existing) {
            if (!has(column)) {
                return existing;
            }
            String v = str(column);
            if (v == null) {
                return existing;
            }
            return switch (v.toLowerCase()) {
                case "true", "yes", "y", "1", "x" -> Boolean.TRUE;
                case "false", "no", "n", "0" -> Boolean.FALSE;
                default -> throw new ImportRowException(column + ": '" + v + "' must be TRUE or FALSE");
            };
        }
    }
}
