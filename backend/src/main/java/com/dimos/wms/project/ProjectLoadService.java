package com.dimos.wms.project;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import com.dimos.wms.identity.ProjectAdminApi;
import com.dimos.wms.identity.WmsUserPrincipal;
import com.dimos.wms.masterdata.ProjectDataApi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads a project data set. Mode REPLACE first deletes ALL master data of the site, then loads the files; mode UPSERT
 * only adds and updates. Everything runs in ONE transaction: the data is replaced completely or not at all. Only a
 * super administrator may do it. Audit rows of the single changes are held back and written after the commit, together
 * with one summary entry that carries the counts deleted and loaded.
 */
@Service
public class ProjectLoadService {

    public enum Mode { REPLACE, UPSERT }

    public record ProjectInfo(String code, String name, String description, boolean bundled, List<FileInfo> files) {
    }

    public record FileInfo(String type, String file, long rows) {
    }

    /** What a load would do: the data set, what exists now (REPLACE would delete it) and the rows to load. */
    public record Preview(ProjectInfo project, Map<String, Long> existing) {
    }

    public record StepInfo(String type, String file, long created, long updated, long unchanged, long errors,
                           List<String> messages, long millis) {
    }

    public record Result(String project, Mode mode, boolean dryRun, boolean applied, Map<String, Long> deleted,
                         List<StepInfo> steps, ProjectDataApi.CheckResult check, long durationMs, List<String> problems) {
    }

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final ProjectCatalogue catalogue;
    private final ProjectDataApi masterData;
    private final ProjectAdminApi admin;
    private final AuditService audit;
    private final TransactionTemplate transactions;

    ProjectLoadService(ProjectCatalogue catalogue, ProjectDataApi masterData, ProjectAdminApi admin, AuditService audit,
                       PlatformTransactionManager transactionManager) {
        this.catalogue = catalogue;
        this.masterData = masterData;
        this.admin = admin;
        this.audit = audit;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    private static void requireSuperAdmin(WmsUserPrincipal who) {
        if (who == null || !who.roles().contains(SUPER_ADMIN)) {
            throw new ProjectException(403, "Only a super administrator can load project data.");
        }
    }

    public List<ProjectInfo> list(WmsUserPrincipal who) {
        requireSuperAdmin(who);
        return catalogue.list().stream().map(p -> info(p, false)).toList();
    }

    public Preview preview(String code, WmsUserPrincipal who) {
        requireSuperAdmin(who);
        ProjectCatalogue.Project p = catalogue.get(code);
        return new Preview(info(p, true), masterData.counts(who.siteId()));
    }

    private ProjectInfo info(ProjectCatalogue.Project p, boolean withRows) {
        List<FileInfo> files = new ArrayList<>();
        for (ProjectCatalogue.FileRef f : p.files()) {
            long rows = -1;
            if (withRows) {
                try {
                    rows = masterData.readRows(f.file(), catalogue.read(p, f.file())).size();
                } catch (RuntimeException ex) {
                    rows = -1; // the problem is reported when the file is checked or loaded
                }
            }
            files.add(new FileInfo(f.type(), f.file(), rows));
        }
        return new ProjectInfo(p.code(), p.name(), p.description(), p.bundled(), files);
    }

    private record Loaded(ProjectCatalogue.FileRef ref, byte[] data) {
    }

    /**
     * @param dryRun  do everything, then roll back: shows exactly what a real load would delete and load
     * @param confirm for REPLACE: the project name, typed by the user (not needed for a dry run)
     */
    public Result load(String code, Mode mode, boolean dryRun, String confirm, WmsUserPrincipal who) {
        requireSuperAdmin(who);
        long started = System.nanoTime();
        ProjectCatalogue.Project project = catalogue.get(code);
        if (mode == Mode.REPLACE && !dryRun
                && (confirm == null || !confirm.trim().equalsIgnoreCase(project.name().trim()))) {
            throw new ProjectException(400, "To replace the master data, type the project name exactly: " + project.name());
        }

        // Read every file BEFORE anything is touched.
        List<String> problems = new ArrayList<>();
        List<Loaded> files = new ArrayList<>();
        for (ProjectCatalogue.FileRef ref : project.files()) {
            boolean known = ProjectDataApi.FILE_KEYS.contains(ref.type()) || ProjectAdminApi.FILE_KEYS.contains(ref.type());
            if (!known) {
                problems.add("project.json: unknown file type '" + ref.type() + "'");
                continue;
            }
            try {
                files.add(new Loaded(ref, catalogue.read(project, ref.file())));
            } catch (ProjectException ex) {
                problems.add(ex.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            return new Result(project.code(), mode, dryRun, false, Map.of(), List.of(), null, millis(started), problems);
        }

        List<StepInfo> steps = new ArrayList<>();
        Map<String, Long> deleted = new LinkedHashMap<>();
        ProjectDataApi.CheckResult[] check = new ProjectDataApi.CheckResult[1];

        AuditService.startHolding();
        boolean applied = false;
        List<AuditEvent> held = List.of();
        try {
            applied = Boolean.TRUE.equals(transactions.execute(tx -> {
                if (mode == Mode.REPLACE) {
                    deleted.putAll(masterData.deleteAll(who.siteId()));
                }
                boolean failed = false;
                for (Loaded f : files) {
                    long stepStarted = System.nanoTime();
                    ProjectDataApi.StepResult r = ProjectDataApi.FILE_KEYS.contains(f.ref().type())
                            ? masterData.load(f.ref().type(), f.ref().file(), f.data(), who)
                            : adminStep(f, who);
                    steps.add(new StepInfo(f.ref().type(), f.ref().file(), r.created(), r.updated(), r.unchanged(),
                            r.errors(), r.messages(), millis(stepStarted)));
                    if (!r.ok()) {
                        failed = true;
                        break; // later files would only produce follow-up errors
                    }
                }
                if (!failed) {
                    check[0] = masterData.check(who.siteId());
                }
                if (failed || dryRun) {
                    tx.setRollbackOnly();
                    return false;
                }
                return true;
            }));
        } finally {
            held = AuditService.endHolding();
        }

        if (applied) {
            List<AuditEvent> all = new ArrayList<>(held);
            all.add(new AuditEvent(who.username(), "PROJECT_LOAD", who.siteId(), "project", project.code(), null, null,
                    summary(project, mode, deleted, steps), null, null));
            audit.recordAll(all);
        }
        return new Result(project.code(), mode, dryRun, applied, deleted, steps, check[0], millis(started), List.of());
    }

    private ProjectDataApi.StepResult adminStep(Loaded f, WmsUserPrincipal who) {
        List<Map<String, String>> rows = masterData.readRows(f.ref().file(), f.data());
        ProjectAdminApi.StepResult r = admin.load(f.ref().type(), rows, who);
        return new ProjectDataApi.StepResult(r.key(), r.created(), r.updated(), r.unchanged(), r.errors(), r.messages());
    }

    private static long millis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private String summary(ProjectCatalogue.Project project, Mode mode, Map<String, Long> deleted, List<StepInfo> steps) {
        StringBuilder sb = new StringBuilder("Project data '").append(project.name()).append("' loaded, mode ").append(mode);
        if (mode == Mode.REPLACE) {
            sb.append(". Deleted: ");
            deleted.forEach((k, v) -> sb.append(k).append('=').append(v).append(' '));
        }
        sb.append(". Loaded: ");
        for (StepInfo s : steps) {
            sb.append(s.type()).append(" (").append(s.created()).append(" created, ").append(s.updated()).append(" updated, ")
                    .append(s.unchanged()).append(" unchanged); ");
        }
        return sb.length() > 990 ? sb.substring(0, 990) : sb.toString();
    }
}
