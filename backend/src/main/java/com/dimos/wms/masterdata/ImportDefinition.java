package com.dimos.wms.masterdata;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * What can be imported/exported for one table: its columns, how a file is processed, and how current data is exported.
 * {@code runner} writes through the normal services, so the checks are exactly the same as in the web forms.
 */
record ImportDefinition(String key, String title, List<Column> columns, BiConsumer<Table, ImportRun> runner,
                        Function<Long, List<List<String>>> exporter) {

    record Column(String name, boolean required, String example, String help) {
    }

    List<String> headers() {
        return columns.stream().map(Column::name).toList();
    }

    List<String> exampleRow() {
        return columns.stream().map(Column::example).toList();
    }

    static Column req(String name, String example, String help) {
        return new Column(name, true, example, help);
    }

    static Column opt(String name, String example, String help) {
        return new Column(name, false, example, help);
    }
}
