package com.dimos.wms.masterdata;

import com.dimos.wms.identity.WmsUserPrincipal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.data.jpa.domain.Specification;

/** The state of one import: the user, the per-row results, and lookups of records by code. */
final class ImportRun {

    enum Status { CREATE, UPDATE, ADD, UNCHANGED, ERROR }

    record RowResult(int row, String key, Status status, String message) {
    }

    final WmsUserPrincipal who;
    final List<RowResult> results = new ArrayList<>();
    private final Set<String> seenKeys = new HashSet<>();

    ImportRun(WmsUserPrincipal who) {
        this.who = who;
    }

    Long siteId() {
        return who.siteId();
    }

    /** Runs the action for one row; whatever goes wrong becomes an error on that row (the import goes on). */
    void row(int number, String key, Supplier<Status> action) {
        if (!seenKeys.add(key)) {
            results.add(new RowResult(number, key, Status.ERROR, "the same record appears twice in this file"));
            return;
        }
        try {
            results.add(new RowResult(number, key, action.get(), null));
        } catch (ImportRowException ex) {
            results.add(new RowResult(number, key, Status.ERROR, ex.getMessage()));
        } catch (MasterDataException ex) {
            results.add(new RowResult(number, key, Status.ERROR,
                    ex.details.isEmpty() ? ex.getMessage() : String.join("; ", ex.details)));
        } catch (RuntimeException ex) {
            results.add(new RowResult(number, key, Status.ERROR, "unexpected problem: " + ex.getClass().getSimpleName()));
        }
    }

    /** Turns an already reported row into an error (used when a grouped write fails after the row check). */
    void fail(int number, String message) {
        for (int i = 0; i < results.size(); i++) {
            RowResult r = results.get(i);
            if (r.row() == number) {
                results.set(i, new RowResult(number, r.key(), Status.ERROR, message));
                return;
            }
        }
    }

    long count(Status status) {
        return results.stream().filter(r -> r.status() == status).count();
    }

    // ---- lookups ----------------------------------------------------------------------------------------

    /** The record of this site with the given code, or empty. */
    <E extends SiteEntity> Optional<E> find(SiteRepository<E> repository, String code) {
        if (code == null) {
            return Optional.empty();
        }
        Specification<E> spec = Specs.<E>bySite(siteId()).and((root, q, cb) -> cb.equal(root.get("code"), code));
        return repository.findOne(spec);
    }

    /** The record with the given code, or a row error "column: unknown what 'CODE'". */
    <E extends SiteEntity> E require(SiteRepository<E> repository, String code, String column, String what) {
        if (code == null) {
            throw new ImportRowException(column + ": is required");
        }
        return find(repository, code.toUpperCase()).orElseThrow(
                () -> new ImportRowException(column + ": unknown " + what + " '" + code + "'"));
    }

    /** Like {@link #require} but an empty cell is fine and gives null. */
    <E extends SiteEntity> E optional(SiteRepository<E> repository, String code, String column, String what) {
        return code == null ? null : require(repository, code, column, what);
    }
}
