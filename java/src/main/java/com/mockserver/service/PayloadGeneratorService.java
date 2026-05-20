package com.mockserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Generates mock payloads of an exact target size (in kilobytes).
 *
 * <p>
 * Supported formats: {@code json} (default), {@code xml}, {@code text},
 * and {@code html}.
 *
 * <p>
 * Valid sizes (KB): 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20
 *
 * <h3>Strategy</h3>
 * <ol>
 * <li>Build a structured envelope (metadata + items list).</li>
 * <li>Measure its serialised byte length.</li>
 * <li>Pad a dedicated padding field/block with ASCII characters until
 * the total byte count reaches exactly {@code targetKb * 1024}.</li>
 * </ol>
 */
@Service
public class PayloadGeneratorService {

    /** Allowed payload sizes in KB. */
    public static final List<Integer> VALID_SIZES_KB = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20);

    private final ObjectMapper jsonMapper;
    private final XmlMapper xmlMapper;

    public PayloadGeneratorService(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.xmlMapper = new XmlMapper();
        this.xmlMapper.configure(
                com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT, true);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generate a payload.
     *
     * @param sizeKb   Target size in KB (must be one of {@link #VALID_SIZES_KB}).
     * @param format   {@code "json"}, {@code "xml"}, {@code "text"}, or {@code "html"}.
     * @param method   HTTP method label (GET / POST / PUT / DELETE).
     * @param resource Logical resource name (e.g. "orders", "users").
     * @return Raw string payload of approximately {@code sizeKb} KB.
     */
    public String generate(int sizeKb, String format, String method, String resource) {
        validateSize(sizeKb);
        int targetBytes = sizeKb * 1024;

        Map<String, Object> envelope = buildEnvelope(sizeKb, method, resource);

        try {
            if ("xml".equalsIgnoreCase(format)) {
                return padXml(envelope, targetBytes, resource);
            } else if ("text".equalsIgnoreCase(format)) {
                return padText(envelope, targetBytes);
            } else if ("html".equalsIgnoreCase(format)) {
                return padHtml(envelope, targetBytes, method, resource);
            } else {
                return padJson(envelope, targetBytes);
            }
        } catch (Exception e) {
            throw new RuntimeException("Payload generation failed", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private void validateSize(int sizeKb) {
        if (!VALID_SIZES_KB.contains(sizeKb)) {
            throw new IllegalArgumentException(
                    "Invalid size: " + sizeKb + " KB. Valid values: " + VALID_SIZES_KB);
        }
    }

    /**
     * Builds a structured map used as the serialisation source.
     * Items are populated initially with 5 sample records; padding is added later.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildEnvelope(int sizeKb, String method, String resource) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("server", "MockAPIServer/1.0");
        meta.put("timestamp", Instant.now().toString());
        meta.put("method", method.toUpperCase());
        meta.put("resource", resource);
        meta.put("targetSizeKb", sizeKb);
        meta.put("requestId", UUID.randomUUID().toString());
        meta.put("status", "200 OK");
        meta.put("contentType", "application/json");

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            items.add(buildItem(i, resource, ""));
        }

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("metadata", meta);
        envelope.put("count", items.size());
        envelope.put("items", items);
        return envelope;
    }

    /** Builds a single mock item record. */
    private Map<String, Object> buildItem(int index, String resource, String padding) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", UUID.randomUUID().toString());
        item.put("index", index);
        item.put("resource", resource);
        item.put("name", "Mock " + resource + " #" + index);
        item.put("description", "Auto-generated mock record for " + resource);
        item.put("active", true);
        item.put("score", Math.round(Math.random() * 100));
        item.put("tags", Arrays.asList("mock", resource, "generated"));
        item.put("createdAt", Instant.now().toString());
        item.put("attributes", buildAttributes(index));
        item.put("padding", padding); // filled during size-fitting
        return item;
    }

    /** Nested attributes block to add structural depth. */
    private Map<String, Object> buildAttributes(int seed) {
        Map<String, Object> attr = new LinkedHashMap<>();
        attr.put("color", pickFrom(seed, "red", "green", "blue", "yellow", "purple"));
        attr.put("size", pickFrom(seed + 1, "small", "medium", "large", "xlarge"));
        attr.put("weight", (seed * 13.7));
        attr.put("priority", seed % 5 + 1);
        attr.put("region", pickFrom(seed + 2, "APAC", "EMEA", "AMER", "LATAM"));
        return attr;
    }

    private String pickFrom(int seed, String... values) {
        return values[Math.abs(seed) % values.length];
    }

    // ── JSON padding ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String padJson(Map<String, Object> envelope, int targetBytes) throws Exception {
        // Measure without padding
        String base = jsonMapper.writeValueAsString(envelope);
        int current = base.getBytes(StandardCharsets.UTF_8).length;

        if (current >= targetBytes) {
            return base;
        }

        // Add padding to last item
        List<Map<String, Object>> items = (List<Map<String, Object>>) envelope.get("items");
        Map<String, Object> lastItem = items.get(items.size() - 1);

        // Rough estimation: each char is 1 byte in UTF-8 for ASCII
        String rough = jsonMapper.writeValueAsString(envelope);
        int roughLen = rough.getBytes(StandardCharsets.UTF_8).length;
        int padNeeded = targetBytes - roughLen;

        if (padNeeded > 0) {
            lastItem.put("padding", buildPadString(padNeeded));
        }

        // Fine-tune: iterate to exact size
        String result = jsonMapper.writeValueAsString(envelope);
        int resultLen = result.getBytes(StandardCharsets.UTF_8).length;
        int diff = targetBytes - resultLen;

        if (diff > 0) {
            String existingPad = (String) lastItem.get("padding");
            lastItem.put("padding", existingPad + buildPadString(diff));
            result = jsonMapper.writeValueAsString(envelope);
        } else if (diff < 0) {
            String existingPad = (String) lastItem.getOrDefault("padding", "");
            int trimTo = Math.max(0, existingPad.length() + diff);
            lastItem.put("padding", existingPad.substring(0, trimTo));
            result = jsonMapper.writeValueAsString(envelope);
        }

        return result;
    }

    // ── XML padding ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String padXml(Map<String, Object> envelope, int targetBytes, String resource)
            throws Exception {

        // Wrap in a typed root to get a proper XML root element
        String base = serializeXml(envelope, resource);
        int current = base.getBytes(StandardCharsets.UTF_8).length;

        if (current >= targetBytes) {
            return base;
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) envelope.get("items");
        Map<String, Object> lastItem = items.get(items.size() - 1);

        int padNeeded = targetBytes - current;
        lastItem.put("padding", buildPadString(padNeeded));

        String result = serializeXml(envelope, resource);
        int resultLen = result.getBytes(StandardCharsets.UTF_8).length;
        int diff = targetBytes - resultLen;

        if (diff > 0) {
            String existing = (String) lastItem.get("padding");
            lastItem.put("padding", existing + buildPadString(diff));
            result = serializeXml(envelope, resource);
        } else if (diff < 0) {
            String existing = (String) lastItem.getOrDefault("padding", "");
            int trimTo = Math.max(0, existing.length() + diff);
            lastItem.put("padding", existing.substring(0, trimTo));
            result = serializeXml(envelope, resource);
        }

        return result;
    }

    private String serializeXml(Map<String, Object> envelope, String resource) throws Exception {
        // Wrap map in an XML root element named after the resource
        return xmlMapper
                .writer()
                .withRootName("MockResponse")
                .writeValueAsString(envelope);
    }

    // ── Plain-text padding ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String padText(Map<String, Object> envelope, int targetBytes) {
        Map<String, Object> meta = (Map<String, Object>) envelope.get("metadata");
        List<Map<String, Object>> items = (List<Map<String, Object>>) envelope.get("items");

        StringBuilder sb = new StringBuilder();
        sb.append("=== Mock API Server Response ===\n");
        sb.append("server      : ").append(meta.get("server")).append('\n');
        sb.append("timestamp   : ").append(meta.get("timestamp")).append('\n');
        sb.append("method      : ").append(meta.get("method")).append('\n');
        sb.append("resource    : ").append(meta.get("resource")).append('\n');
        sb.append("targetSizeKb: ").append(meta.get("targetSizeKb")).append('\n');
        sb.append("requestId   : ").append(meta.get("requestId")).append('\n');
        sb.append("status      : ").append(meta.get("status")).append('\n');
        sb.append("count       : ").append(items.size()).append('\n');
        sb.append("---\n");
        for (Map<String, Object> item : items) {
            sb.append("[item]\n");
            item.forEach((k, v) -> {
                if (!"padding".equals(k)) {
                    sb.append("  ").append(k).append(" = ").append(v).append('\n');
                }
            });
        }
        sb.append("[padding]\n");
        String base = sb.toString();
        int current = base.getBytes(StandardCharsets.UTF_8).length;
        int padNeeded = targetBytes - current;
        // account for the newline after padding
        padNeeded = Math.max(0, padNeeded - 1);
        return base + buildPadString(padNeeded) + '\n';
    }

    // ── HTML padding ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String padHtml(Map<String, Object> envelope, int targetBytes,
                           String method, String resource) {
        Map<String, Object> meta = (Map<String, Object>) envelope.get("metadata");
        List<Map<String, Object>> items = (List<Map<String, Object>>) envelope.get("items");

        // Build table rows
        StringBuilder rows = new StringBuilder();
        for (Map<String, Object> item : items) {
            rows.append("<tr>");
            rows.append("<td>").append(item.get("id")).append("</td>");
            rows.append("<td>").append(item.get("index")).append("</td>");
            rows.append("<td>").append(item.get("name")).append("</td>");
            rows.append("<td>").append(item.get("description")).append("</td>");
            rows.append("<td>").append(item.get("active")).append("</td>");
            rows.append("<td>").append(item.get("score")).append("</td>");
            rows.append("<td>").append(item.get("createdAt")).append("</td>");
            rows.append("</tr>\n");
        }

        // Build HTML skeleton (padding comment at end)
        String template =
            "<!DOCTYPE html>\n" +
            "<html lang=\"en\">\n" +
            "<head><meta charset=\"UTF-8\">" +
            "<title>Mock API Response — " + resource + "</title>\n" +
            "<style>" +
            "body{font-family:sans-serif;background:#0d1117;color:#e6edf3;margin:24px}" +
            "h1{color:#58a6ff;font-size:1.4rem;margin-bottom:8px}" +
            ".meta{font-size:.85rem;color:#8b949e;margin-bottom:16px}" +
            "table{border-collapse:collapse;width:100%}" +
            "th{background:#21262d;color:#58a6ff;padding:8px 12px;text-align:left;border:1px solid #30363d}" +
            "td{padding:7px 12px;border:1px solid #30363d;font-size:.85rem}" +
            "tr:nth-child(even){background:#161b22}" +
            "</style></head>\n" +
            "<body>\n" +
            "<h1>Mock API Response</h1>\n" +
            "<div class=\"meta\">" +
              "<strong>" + method.toUpperCase() + "</strong> /" + resource +
              " &nbsp;|&nbsp; requestId: " + meta.get("requestId") +
              " &nbsp;|&nbsp; " + meta.get("timestamp") +
            "</div>\n" +
            "<table>\n" +
            "<thead><tr>" +
              "<th>ID</th><th>#</th><th>Name</th><th>Description</th>" +
              "<th>Active</th><th>Score</th><th>Created At</th>" +
            "</tr></thead>\n" +
            "<tbody>\n" + rows + "</tbody>\n" +
            "</table>\n" +
            "<!-- [padding] ";
        String tail = " -->\n</body>\n</html>";

        int baseLen = (template + tail).getBytes(StandardCharsets.UTF_8).length;
        int padNeeded = Math.max(0, targetBytes - baseLen);
        return template + buildPadString(padNeeded) + tail;
    }

    /**
     * Generates a repeating ASCII pad string of exactly {@code length} characters.
     */
    private String buildPadString(int length) {
        if (length <= 0)
            return "";
        String unit = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        int repeat = (length / unit.length()) + 1;
        StringBuilder sb = new StringBuilder(repeat * unit.length());
        for (int i = 0; i < repeat; i++)
            sb.append(unit);
        return sb.substring(0, length);
    }
}
