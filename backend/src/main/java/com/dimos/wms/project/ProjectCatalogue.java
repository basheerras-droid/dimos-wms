package com.dimos.wms.project;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Finds the project data sets. A data set is a folder with a {@code project.json} (name, description, ordered list of
 * files) and the CSV/XLSX files it names. Two places are searched: {@code projects/} inside the application (what is
 * shipped with the product, e.g. the demo plant) and the folder {@code wms.projects.dir} on the server (customer
 * data, which never goes into Git). A folder on the server wins over a bundled one with the same code.
 */
@Component
class ProjectCatalogue {

    /** One file of a data set: its type (a master-data or administration file key) and its name in the folder. */
    record FileRef(String type, String file) {
    }

    record Project(String code, String name, String description, boolean bundled, List<FileRef> files, String base) {
    }

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    private final JsonMapper json;
    private final String externalDir;

    ProjectCatalogue(JsonMapper json, @Value("${wms.projects.dir:}") String externalDir) {
        this.json = json;
        this.externalDir = externalDir == null ? "" : externalDir.trim();
    }

    List<Project> list() {
        Map<String, Project> byCode = new LinkedHashMap<>();
        scan("classpath*:projects/*/project.json", true, byCode);
        if (!externalDir.isEmpty()) {
            String dir = externalDir.replace('\\', '/');
            scan("file:" + (dir.endsWith("/") ? dir : dir + "/") + "*/project.json", false, byCode);
        }
        return byCode.values().stream().sorted((a, b) -> a.code().compareTo(b.code())).toList();
    }

    Project get(String code) {
        return list().stream().filter(p -> p.code().equalsIgnoreCase(code)).findFirst()
                .orElseThrow(() -> new ProjectException(404, "Unknown project data set '" + code + "'."));
    }

    byte[] read(Project project, String file) {
        if (file.contains("..") || file.contains("/") || file.contains("\\")) {
            throw new ProjectException(400, "Invalid file name '" + file + "' in project.json.");
        }
        try (InputStream in = resolver.getResource(project.base() + "/" + file).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new ProjectException(422, "The file '" + file + "' of project '" + project.code() + "' cannot be read.");
        }
    }

    private void scan(String pattern, boolean bundled, Map<String, Project> into) {
        Resource[] manifests;
        try {
            manifests = resolver.getResources(pattern);
        } catch (IOException ex) {
            return; // folder does not exist: nothing to list
        }
        for (Resource manifest : manifests) {
            try (InputStream in = manifest.getInputStream()) {
                JsonNode root = json.readTree(in);
                String code = root.path("code").asString("").trim().toLowerCase();
                if (!code.matches("[a-z0-9][a-z0-9_-]{0,29}")) {
                    continue;
                }
                List<FileRef> files = new ArrayList<>();
                for (JsonNode f : root.path("files")) {
                    files.add(new FileRef(f.path("type").asString(""), f.path("file").asString("")));
                }
                String url = manifest.getURL().toString();
                String base = url.substring(0, url.length() - "/project.json".length());
                into.put(code, new Project(code, root.path("name").asString(code), root.path("description").asString(""),
                        bundled, files, base));
            } catch (IOException | RuntimeException ex) {
                // a damaged manifest hides that data set only
            }
        }
    }
}
