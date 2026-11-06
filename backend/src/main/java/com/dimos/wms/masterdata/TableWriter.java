package com.dimos.wms.masterdata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/** Writes a header row and data rows as CSV (UTF-8 with BOM so Excel shows special characters) or XLSX. */
final class TableWriter {

    static final String CSV = "text/csv; charset=UTF-8";
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private TableWriter() {
    }

    static boolean isXlsx(String format) {
        return "xlsx".equalsIgnoreCase(format);
    }

    static byte[] write(boolean xlsx, String sheetName, List<String> headers, List<List<String>> rows) {
        try {
            return xlsx ? xlsx(sheetName, headers, rows) : csv(headers, rows);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write the file", ex);
        }
    }

    private static byte[] csv(List<String> headers, List<List<String>> rows) throws IOException {
        StringWriter out = new StringWriter();
        out.write('﻿');
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT)) {
            printer.printRecord(headers);
            for (List<String> row : rows) {
                printer.printRecord(row.stream().map(TableWriter::safeForSpreadsheet).toList());
            }
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** A text starting with = + @ (or - followed by a letter) could be run as a formula when opened in Excel. */
    static String safeForSpreadsheet(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char c = value.charAt(0);
        boolean risky = c == '=' || c == '+' || c == '@' || c == '\t' || c == '\r'
                || (c == '-' && value.length() > 1 && !Character.isDigit(value.charAt(1)) && value.charAt(1) != '.');
        return risky ? "'" + value : value;
    }

    private static byte[] xlsx(String sheetName, List<String> headers, List<List<String>> rows) throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        try {
            Sheet sheet = workbook.createSheet(sheetName);
            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle head = workbook.createCellStyle();
            head.setFont(bold);
            Row header = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                header.createCell(c).setCellValue(headers.get(c));
                header.getCell(c).setCellStyle(head);
            }
            int r = 1;
            for (List<String> values : rows) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < values.size(); c++) {
                    if (values.get(c) != null) {
                        row.createCell(c).setCellValue(values.get(c)); // always a text cell: never a formula
                    }
                }
            }
            sheet.createFreezePane(0, 1);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }
}
