package com.dimos.wms.project;

import com.dimos.wms.identity.WmsUserPrincipal;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Project data sets: list, preview (what exists now), and load (check only, or really). SUPER_ADMIN only. */
@RestController
@RequestMapping("/api/projects")
@PreAuthorize("hasRole('SUPER_ADMIN')")
class ProjectController {

    record LoadRequest(ProjectLoadService.Mode mode, boolean dryRun, String confirm) {
    }

    record ApiError(String code, String message, List<String> details, String correlationId) {
    }

    private final ProjectLoadService service;

    ProjectController(ProjectLoadService service) {
        this.service = service;
    }

    @GetMapping
    List<ProjectLoadService.ProjectInfo> list(@AuthenticationPrincipal WmsUserPrincipal who) {
        return service.list(who);
    }

    @GetMapping("/{code}")
    ProjectLoadService.Preview preview(@PathVariable String code, @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.preview(code, who);
    }

    @PostMapping("/{code}/load")
    ResponseEntity<ProjectLoadService.Result> load(@PathVariable String code, @RequestBody LoadRequest request,
                                                   @AuthenticationPrincipal WmsUserPrincipal who) {
        ProjectLoadService.Mode mode = request.mode() == null ? ProjectLoadService.Mode.UPSERT : request.mode();
        ProjectLoadService.Result result = service.load(code, mode, request.dryRun(), request.confirm(), who);
        boolean refused = !request.dryRun() && !result.applied();
        return ResponseEntity.status(refused ? HttpStatus.UNPROCESSABLE_CONTENT : HttpStatus.OK).body(result);
    }

    @ExceptionHandler(ProjectException.class)
    ResponseEntity<ApiError> refused(ProjectException ex) {
        return ResponseEntity.status(ex.status())
                .body(new ApiError("PROJECT_ERROR", ex.getMessage(), List.of(), MDC.get("correlationId")));
    }
}
