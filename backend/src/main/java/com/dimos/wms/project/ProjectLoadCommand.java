package com.dimos.wms.project;

import com.dimos.wms.identity.PermissionCatalogue;
import com.dimos.wms.identity.WmsUserPrincipal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Loads a project data set from the command line, without the web screen, and then stops the application. Only active
 * when {@code wms.project-load.code} is given. It DEFAULTS TO A DRY RUN (nothing is changed) - add
 * {@code --wms.project-load.dry-run=false} to really load.
 *
 * <pre>
 * java -jar wms-backend.jar --spring.main.web-application-type=none --wms.project-load.code=demo
 *      --wms.project-load.mode=REPLACE --wms.project-load.dry-run=false --wms.project-load.confirm="Demo plant"
 * </pre>
 */
@Component
@ConditionalOnProperty("wms.project-load.code")
class ProjectLoadCommand implements ApplicationRunner {

    private final ProjectLoadService service;
    private final ConfigurableApplicationContext context;
    private final String code;
    private final ProjectLoadService.Mode mode;
    private final boolean dryRun;
    private final String confirm;
    private final long siteId;
    private final String actor;

    ProjectLoadCommand(ProjectLoadService service, ConfigurableApplicationContext context,
                       @Value("${wms.project-load.code}") String code,
                       @Value("${wms.project-load.mode:UPSERT}") ProjectLoadService.Mode mode,
                       @Value("${wms.project-load.dry-run:true}") boolean dryRun,
                       @Value("${wms.project-load.confirm:}") String confirm,
                       @Value("${wms.project-load.site-id:1}") long siteId,
                       @Value("${wms.project-load.actor:project-loader}") String actor) {
        this.service = service;
        this.context = context;
        this.code = code;
        this.mode = mode;
        this.dryRun = dryRun;
        this.confirm = confirm;
        this.siteId = siteId;
        this.actor = actor;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exit = 0;
        try {
            WmsUserPrincipal who = new WmsUserPrincipal(0L, siteId, actor, "Project loader (command line)",
                    List.of("SUPER_ADMIN"), PermissionCatalogue.ALL.stream().map(PermissionCatalogue.Entry::code).toList(), false);
            ProjectLoadService.Result r = service.load(code, mode, dryRun, confirm, who);
            System.out.println("PROJECT-LOAD project=" + r.project() + " mode=" + r.mode() + " dryRun=" + r.dryRun()
                    + " applied=" + r.applied() + " seconds=" + r.durationMs() / 1000.0);
            r.problems().forEach(p -> System.out.println("PROJECT-LOAD problem: " + p));
            r.deleted().forEach((k, v) -> System.out.println("PROJECT-LOAD deleted " + k + " = " + v));
            for (ProjectLoadService.StepInfo s : r.steps()) {
                System.out.printf("PROJECT-LOAD step %-24s created=%-6d updated=%-5d unchanged=%-6d errors=%-3d %6.1f s%n",
                        s.type(), s.created(), s.updated(), s.unchanged(), s.errors(), s.millis() / 1000.0);
                s.messages().forEach(m -> System.out.println("PROJECT-LOAD   " + m));
            }
            if (r.check() != null) {
                System.out.println("PROJECT-LOAD check " + r.check().status() + " errors=" + r.check().errors()
                        + " warnings=" + r.check().warnings());
                r.check().messages().forEach(m -> System.out.println("PROJECT-LOAD   " + m));
            }
            if (!r.problems().isEmpty() || (!dryRun && !r.applied())) {
                exit = 2;
            }
        } catch (ProjectException ex) {
            System.out.println("PROJECT-LOAD refused: " + ex.getMessage());
            exit = 2;
        }
        int code = exit;
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
