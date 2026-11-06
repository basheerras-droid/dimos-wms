package com.dimos.wms.masterdata;

import com.dimos.wms.identity.WmsUserPrincipal;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Import: {@code POST /import/{type}?dryRun=true} checks a file and changes nothing;
 * {@code dryRun=false} applies it (all rows or none). Templates and exports are CSV or XLSX.
 */
@RestController
@RequestMapping("/api/masterdata")
class ImportExportController {

    private final ImportExportService service;

    ImportExportController(ImportExportService service) {
        this.service = service;
    }

    @GetMapping("/import/types")
    @PreAuthorize(MasterDataController.VIEW)
    List<ImportExportService.EntityInfo> types() {
        return service.entities();
    }

    @GetMapping("/import/{type}/template")
    @PreAuthorize(MasterDataController.VIEW)
    ResponseEntity<byte[]> template(@PathVariable String type, @RequestParam(defaultValue = "csv") String format) {
        boolean xlsx = TableWriter.isXlsx(format);
        return download(service.template(type, xlsx), type + "-template", xlsx);
    }

    @GetMapping("/export/{type}")
    @PreAuthorize(MasterDataController.VIEW)
    ResponseEntity<byte[]> export(@PathVariable String type, @RequestParam(defaultValue = "csv") String format,
                                  @AuthenticationPrincipal WmsUserPrincipal who) {
        boolean xlsx = TableWriter.isXlsx(format);
        return download(service.export(type, xlsx, who), type, xlsx);
    }

    @PostMapping("/import/{type}")
    @PreAuthorize(MasterDataController.EDIT)
    ResponseEntity<ImportExportService.Result> importFile(@PathVariable String type,
                                                          @RequestParam("file") MultipartFile file,
                                                          @RequestParam(defaultValue = "true") boolean dryRun,
                                                          @AuthenticationPrincipal WmsUserPrincipal who)
            throws IOException {
        ImportExportService.Result result = service.process(type, file.getOriginalFilename(), file.getBytes(), dryRun,
                who);
        // Apply with problems: nothing was written, and the status says so.
        boolean refused = !dryRun && !result.applied();
        return ResponseEntity.status(refused ? HttpStatus.UNPROCESSABLE_CONTENT : HttpStatus.OK).body(result);
    }

    private static ResponseEntity<byte[]> download(byte[] body, String name, boolean xlsx) {
        String fileName = name + (xlsx ? ".xlsx" : ".csv");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(MediaType.parseMediaType(xlsx ? TableWriter.XLSX : TableWriter.CSV))
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }
}
