package com.dimos.wms.masterdata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/** Reads a CSV or XLSX upload into a {@link Table}. */
final class TableReader {

    static final int MAX_ROWS = 50_000;

    private TableReader() {
    }

    static Table read(String filename, byte[] data) {
        String name = filename == null ? "" : filename.toLowerCase();
        if (data == null || data.length == 0) {
            throw new ImportFileException("The file is empty.");
        }
        try {
            if (name.endsWith(".xlsx")) {
                return readXlsx(data);
            }
            if (name.endsWith(".csv") || name.endsWith(".txt")) {
                return readCsv(data);
            }
        } catch (ImportFileException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new ImportFileException("The file could not be read (" + ex.getClass().getSimpleName()
                    + "). Use a .csv or .xlsx file made from the template.");
        }
        throw new ImportFileException("Use a .csv or .xlsx file.");
    }

    // ---- CSV ---------------------------------------------------------------------------------------------

    private static Table readCsv(byte[] data) throws IOException {
        String text = decode(data);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        char delimiter = detectDelimiter(text);
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setIgnoreEmptyLines(true)
                .setTrim(true).get();
        List<String> headers = null;
        List<Table.Line> lines = new ArrayList<>();
        try (CSVParser parser = format.parse(new StringReader(text))) {
            for (CSVRecord record : parser) {
                if (headers == null) {
                    headers = new ArrayList<>(record.toList());
                    continue;
                }
                if (lines.size() >= MAX_ROWS) {
                    throw tooMany();
                }
                List<String> cells = record.toList();
                checkSeparator(delimiter, headers.size(), cells, (int) record.getRecordNumber());
                Map<String, String> values = new LinkedHashMap<>();
                boolean any = false;
                for (int i = 0; i < headers.size(); i++) {
                    String cell = i < cells.size() ? cells.get(i) : "";
                    any |= !cell.isBlank();
                    values.put(Table.norm(headers.get(i)), cell);
                }
                if (any) {
                    lines.add(new Table.Line((int) record.getRecordNumber(), values));
                }
            }
        }
        return new Table(headers == null ? List.of() : headers, lines);
    }

    /**
     * A header with several columns but a row that stayed in a single cell, which itself contains another separator,
     * means the file mixes separators (e.g. TAB in the header, commas in the rows). One clear message instead of one
     * confusing error per row.
     */
    private static void checkSeparator(char headerDelimiter, int headerColumns, List<String> cells, int row) {
        if (headerColumns < 2 || cells.size() != 1) {
            return;
        }
        for (char other : new char[] {',', ';', '\t'}) {
            if (other != headerDelimiter && cells.get(0).indexOf(other) >= 0) {
                throw new ImportFileException("The header row uses " + nameOf(headerDelimiter) + " but row " + row
                        + " uses " + nameOf(other) + " - use one separator in the whole file.");
            }
        }
    }

    private static String nameOf(char delimiter) {
        return delimiter == '\t' ? "TAB" : delimiter == ';' ? "semicolon" : "comma";
    }

    /** UTF-8 (with or without BOM); files saved by older Excel versions are Windows-1252. */
    private static String decode(byte[] data) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException ex) {
            return new String(data, Charset.forName("windows-1252"));
        }
    }

    /** Excel in some regions saves CSV with ; or tab. Pick the one that appears most in the header line. */
    private static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String first = end < 0 ? text : text.substring(0, end);
        int commas = count(first, ',');
        int semicolons = count(first, ';');
        int tabs = count(first, '\t');
        if (semicolons > commas && semicolons >= tabs) {
            return ';';
        }
        if (tabs > commas && tabs > semicolons) {
            return '\t';
        }
        return ',';
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            n += s.charAt(i) == c ? 1 : 0;
        }
        return n;
    }

    // ---- XLSX --------------------------------------------------------------------------------------------

    private static Table readXlsx(byte[] data) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<String> headers = null;
            List<Table.Line> lines = new ArrayList<>();
            for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                if (headers == null) {
                    headers = new ArrayList<>();
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        headers.add(text(row.getCell(c), formatter, evaluator));
                    }
                    if (headers.stream().allMatch(String::isBlank)) {
                        headers = null; // blank rows above the header
                    }
                    continue;
                }
                if (lines.size() >= MAX_ROWS) {
                    throw tooMany();
                }
                Map<String, String> values = new LinkedHashMap<>();
                boolean any = false;
                for (int c = 0; c < headers.size(); c++) {
                    String cell = text(row.getCell(c), formatter, evaluator);
                    any |= !cell.isBlank();
                    values.put(Table.norm(headers.get(c)), cell);
                }
                if (any) {
                    lines.add(new Table.Line(r + 1, values));
                }
            }
            return new Table(headers == null ? List.of() : headers, lines);
        }
    }

    private static String text(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        return cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
    }

    private static ImportFileException tooMany() {
        return new ImportFileException("Too many rows (maximum " + MAX_ROWS + " per file).");
    }
}
