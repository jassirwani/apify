package com.jnw.apify.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Controller
public class ApiSpecController {

    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    private static class SpecInfo {
        String filename;
        String id; // unique base64-id derived from resource URI
        String location; // short display path
        String title;
        String description;
        String format;
        long size;

        SpecInfo(String filename) {
            this.filename = filename;
        }
    }

    @GetMapping("/")
    @ResponseBody
    public ResponseEntity<String> index() throws IOException {
        List<SpecInfo> specs = discoverSpecs();

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            .append("<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css\" rel=\"stylesheet\">")
            .append("<link href=\"https://cdn.jsdelivr.net/npm/bootstrap-icons@1.10.5/font/bootstrap-icons.css\" rel=\"stylesheet\">")
            .append("<title>APIs</title>")
            .append("<style> .card-title { font-size: 1rem; font-weight:600;} .card-text{font-size:.9rem;color:#555} .spec-desc{min-height:3.3em; overflow:hidden;} .spec-badge{margin-left:0.5rem;} .spec-path{font-size:.8rem;color:#6c757d}")
            .append("</style>")
            .append("</head><body>")
            .append("<div class=\"container py-4\"><h1 class=\"mb-4\">Available API Specs</h1>")
            .append("<div class=\"row row-cols-1 row-cols-sm-2 row-cols-md-3 row-cols-lg-4 g-4\">");

        for (SpecInfo s : specs) {
            String id = s.id; // already URL-safe base64
            String desc = s.description == null ? "" : escapeHtml(truncate(s.description, 180));
            String title = s.title == null || s.title.isBlank() ? s.filename : escapeHtml(s.title);
            String formatBadge = "<span class=\"badge bg-secondary spec-badge\">" + s.format + "</span>";
            String pathHtml = s.location == null ? "" : "<div class=\"spec-path\">" + escapeHtml(s.location) + "</div>";

            html.append("<div class=\"col\">")
                .append("<div class=\"card h-100 shadow-sm\">")
                .append("<div class=\"card-body d-flex flex-column\">")
                .append("<div class=\"d-flex align-items-start\">")
                .append("<i class=\"bi bi-file-earmark-code fs-2 me-3 text-primary\"></i>")
                .append("<div class=\"flex-grow-1\">")
                .append("<h5 class=\"card-title\">" + title + formatBadge + "</h5>")
                .append(pathHtml)
                .append("<div class=\"card-text spec-desc\">" + desc + "</div>")
                .append("</div></div>")
                .append("<div class=\"mt-3 d-flex gap-2\">")
                .append("<a class=\"btn btn-sm btn-outline-primary\" href=\"/api-spec/view/" + id + "\">Open</a>")
                .append("<a class=\"btn btn-sm btn-outline-secondary\" href=\"/api-spec/content/" + id + "\">Raw</a>")
                .append("<div class=\"ms-auto text-muted small\">" + humanSize(s.size) + "</div>")
                .append("</div>")
                .append("</div></div></div>");
        }

        html.append("</div></div>")
            .append("</body></html>");

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html.toString());
    }

    @GetMapping("/api-spec/view/{id}")
    @ResponseBody
    public ResponseEntity<String> viewSpec(@PathVariable String id) throws IOException {
        Resource res = findResourceByKey(id);
        if (res == null || !res.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Spec not found: " + id);
        }
        String displayName = res.getFilename();
        String specUrl = "/api-spec/content/" + id;

        String html = "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css\" rel=\"stylesheet\">"
                + "<title>" + escapeHtml(displayName) + "</title>"
                + "</head><body style=\"margin:0;\">"
                + "<div class=\"d-flex\" style=\"height:100vh; width:100vw\">"
                + "<div style=\"flex:1;\"><redoc spec-url=\"" + specUrl + "\"></redoc></div>"
                + "</div>"
                + "<script src=\"https://cdn.redoc.ly/redoc/latest/bundles/redoc.standalone.js\"></script>"
                + "</body></html>";

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @GetMapping(path = "/api-spec/content/{id}")
    @ResponseBody
    public ResponseEntity<String> getSpecContent(@PathVariable String id) throws IOException {
        Resource res = findResourceByKey(id);
        if (res == null || !res.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Spec not found: " + id);
        }
        String content = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        MediaType mediaType = detectMediaType(res.getFilename());
        return ResponseEntity.ok().contentType(mediaType).body(content);
    }

    private MediaType detectMediaType(String filename) {
        if (filename != null && filename.endsWith(".json")) return MediaType.APPLICATION_JSON;
        // YAML doesn't have a dedicated constant in MediaType
        return MediaType.valueOf("application/yaml");
    }

    private Resource findResourceByKey(String key) throws IOException {
        List<Resource> resources = new ArrayList<>();
        resources.addAll(Arrays.asList(resolver.getResources("classpath*:**/*.yaml")));
        //resources.addAll(Arrays.asList(resolver.getResources("classpath*:**/*.yml")));
        //resources.addAll(Arrays.asList(resolver.getResources("classpath*:**/*.json")));

        for (Resource r : resources) {
            String filename = r.getFilename();
            if (filename != null && filename.equals(key)) {
                // backward compatibility: allow filename in path
                return r;
            }
            try {
                String raw = r.getURI().toString();
                String id = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
                if (id.equals(key)) return r;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private List<SpecInfo> discoverSpecs() throws IOException {
        List<SpecInfo> infos = new ArrayList<>();
        List<Resource> resources = new ArrayList<>();
        resources.addAll(Arrays.asList(resolver.getResources("classpath*:**/*.yaml")));
        //resources.addAll(Arrays.asList(resolver.getResources("classpath*:**/*.yml")));
        //resources.addAll(Arrays.asList(resolver.getResources("classpath*:**/*.json")));

        for (Resource r : resources) {
            if (r.getFilename() == null) continue;
            SpecInfo info = new SpecInfo(r.getFilename());
            info.format = r.getFilename().endsWith(".json") ? "json" : "yaml";
            byte[] bytes = r.getInputStream().readAllBytes();
            info.size = bytes.length;
            String text = new String(bytes, StandardCharsets.UTF_8);

            // assign id based on resource uri
            try {
                String raw = r.getURI().toString();
                info.id = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
                // compute a short display path
                String display = raw;
                int idx = raw.indexOf("/classes/");
                if (idx >= 0) display = raw.substring(idx + "/classes/".length());
                else if (raw.contains("!/")) display = raw.substring(raw.indexOf("!/") + 2);
                info.location = display;
            } catch (Exception ignored) {
                info.id = info.filename; // fallback
            }

            // try to parse info.title and info.description
            try {
                ObjectMapper mapper = info.format.equals("json") ? new ObjectMapper() : new ObjectMapper(new YAMLFactory());
                @SuppressWarnings("unchecked")
                Map<String, Object> root = mapper.readValue(text, Map.class);
                Object infoNode = root.get("info");
                if (infoNode instanceof Map) {
                    Map<String, Object> infoMap = (Map<String, Object>) infoNode;
                    Object t = infoMap.get("title");
                    Object d = infoMap.get("description");
                    if (t != null) info.title = t.toString();
                    if (d != null) info.description = d.toString();
                }
            } catch (Exception ignored) {
                // ignore parse errors; fall back to filename
            }

            infos.add(info);
        }

        return infos;
    }

    private static String truncate(String s, int l) {
        if (s == null) return null;
        return s.length() <= l ? s : s.substring(0, l - 1) + "…";
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
