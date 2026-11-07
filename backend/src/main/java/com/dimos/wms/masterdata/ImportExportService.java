package com.dimos.wms.masterdata;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import com.dimos.wms.identity.WmsUserPrincipal;
import com.dimos.wms.masterdata.ImportRun.RowResult;
import com.dimos.wms.masterdata.ImportRun.Status;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spreadsheet import in two steps. A dry run runs the real import inside a transaction that is always rolled back,
 * so it reports exactly what Apply would do. Apply writes everything in one transaction or, if any row is wrong,
 * nothing at all. Audit rows are written only after a successful Apply.
 */
@Service
class ImportExportService {

    static final int MAX_ROWS_IN_REPLY = 500;

    record EntityInfo(String key, String title, List<ImportDefinition.Column> columns) {
    }

    record Result(String entity, boolean dryRun, boolean applied, long created, long updated, long added,
                  long unchanged, long errors, boolean truncated, List<String> fileErrors, List<RowResult> rows) {
    }

    private final MasterDataImporters importers;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final EntityManager entityManager;

    ImportExportService(MasterDataImporters importers, PlatformTransactionManager transactionManager,
                        AuditService audit, EntityManager entityManager) {
        this.entityManager = entityManager;
        this.importers = importers;
        this.transactions = new TransactionTemplate(transactionManager);
        this.audit = audit;
    }

    /** All importable tables in the order they have to be loaded. */
    List<EntityInfo> entities() {
        return importers.all().stream().map(d -> new EntityInfo(d.key(), d.title(), d.columns())).toList();
    }

    ImportDefinition definition(String key) {
        return importers.get(key);
    }

    byte[] template(String key, boolean xlsx) {
        ImportDefinition d = importers.get(key);
        return TableWriter.write(xlsx, d.key(), d.headers(), List.of(d.exampleRow()));
    }

    @Transactional(readOnly = true)
    public byte[] export(String key, boolean xlsx, WmsUserPrincipal who) {
        ImportDefinition d = importers.get(key);
        return TableWriter.write(xlsx, d.key(), d.headers(), d.exporter().apply(who.siteId()));
    }

    private record Prepared(Table table, Result problem) {
    }

    /** Reads the file and checks it as a whole; a problem is returned as a ready result. */
    private Prepared prepare(ImportDefinition d, String key, String filename, byte[] data, boolean dryRun) {
        Table table;
        try {
            table = TableReader.read(filename, data);
        } catch (ImportFileException ex) { // the file as a whole is unusable: one clear message, no rows
            return new Prepared(null, new Result(key, dryRun, false, 0, 0, 0, 0, 1, false, List.of(ex.getMessage()), List.of()));
        }
        List<String> fileErrors = checkHeaders(d, table);
        if (!fileErrors.isEmpty()) {
            return new Prepared(null, new Result(key, dryRun, false, 0, 0, 0, 0, fileErrors.size(), false, fileErrors, List.of()));
        }
        if (table.lines().isEmpty()) {
            return new Prepared(null, new Result(key, dryRun, false, 0, 0, 0, 0, 1, false,
                    List.of("The file has a header row but no data rows."), List.of()));
        }
        return new Prepared(table, null);
    }

    private ImportRun newRun(WmsUserPrincipal who) {
        return new ImportRun(who, () -> {
            entityManager.flush();
            entityManager.clear(); // keeps the session small: thousands of rows would otherwise make every flush slower
        });
    }

    /**
     * Runs one file inside the CALLER's transaction and leaves commit or rollback (and the audit rows) to the caller.
     * Used when several files are loaded as one unit (a project data set).
     */
    Result runStep(String key, String filename, byte[] data, WmsUserPrincipal who) {
        ImportDefinition d = importers.get(key);
        Prepared p = prepare(d, key, filename, data, false);
        if (p.problem() != null) {
            return p.problem();
        }
        ImportRun run = newRun(who);
        d.runner().accept(p.table(), run);
        entityManager.flush();
        return result(key, false, false, run);
    }

    public Result process(String key, String filename, byte[] data, boolean dryRun, WmsUserPrincipal who) {
        ImportDefinition d = importers.get(key);
        Prepared prepared = prepare(d, key, filename, data, dryRun);
        if (prepared.problem() != null) {
            return prepared.problem();
        }
        Table table = prepared.table();

        ImportRun run = newRun(who);
        AuditBuffer.begin();
        List<AuditEvent> held;
        try {
            transactions.executeWithoutResult(status -> {
                d.runner().accept(table, run);
                if (dryRun || run.count(Status.ERROR) > 0) {
                    status.setRollbackOnly(); // nothing is written
                }
            });
        } finally {
            held = AuditBuffer.end();
        }

        boolean applied = !dryRun && run.count(Status.ERROR) == 0;
        if (applied) {
            List<AuditEvent> all = new ArrayList<>(held);
            all.add(new AuditEvent(who.username(), "IMPORT", who.siteId(), key, null, null, null,
                    "File " + safeName(filename) + ": " + run.count(Status.CREATE) + " created, "
                            + run.count(Status.UPDATE) + " updated, " + run.count(Status.ADD) + " added, "
                            + run.count(Status.UNCHANGED) + " unchanged",
                    MasterDataService.remoteAddr(), null));
            audit.recordAll(all); // one transaction for all audit rows of the file
        }
        return result(key, dryRun, applied, run);
    }

    private static Result result(String key, boolean dryRun, boolean applied, ImportRun run) {
        List<RowResult> shown = new ArrayList<>();
        int okShown = 0;
        boolean truncated = false;
        for (RowResult r : run.results) {
            if (r.status() == Status.ERROR) {
                shown.add(r);
            } else if (okShown < MAX_ROWS_IN_REPLY) {
                shown.add(r);
                okShown++;
            } else {
                truncated = true;
            }
        }
        return new Result(key, dryRun, applied, run.count(Status.CREATE), run.count(Status.UPDATE),
                run.count(Status.ADD), run.count(Status.UNCHANGED), run.count(Status.ERROR), truncated, List.of(),
                shown);
    }

    /** Unknown, missing and repeated column names are reported for the whole file before any row is looked at. */
    private static List<String> checkHeaders(ImportDefinition d, Table table) {
        List<String> problems = new ArrayList<>();
        Set<String> known = new HashSet<>();
        d.columns().forEach(c -> known.add(Table.norm(c.name())));
        Set<String> seen = new HashSet<>();
        for (String header : table.headers()) {
            String n = Table.norm(header);
            if (n.isEmpty()) {
                continue;
            }
            if (!known.contains(n)) {
                problems.add("Unknown column '" + header + "'. Expected: " + String.join(", ", d.headers()));
            } else if (!seen.add(n)) {
                problems.add("Column '" + header + "' appears twice.");
            }
        }
        for (ImportDefinition.Column c : d.columns()) {
            if (c.required() && !seen.contains(Table.norm(c.name()))) {
                problems.add("Required column '" + c.name() + "' is missing.");
            }
        }
        return problems;
    }

    private static String safeName(String filename) {
        String n = filename == null ? "" : filename.replaceAll("[^A-Za-z0-9._ -]", "_");
        return n.length() > 80 ? n.substring(0, 80) : n;
    }
}
