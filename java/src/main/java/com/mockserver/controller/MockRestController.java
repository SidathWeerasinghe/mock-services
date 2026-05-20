package com.mockserver.controller;

import com.mockserver.service.PayloadGeneratorService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing GET / POST / PUT / DELETE endpoints.
 *
 * <h3>URL pattern</h3>
 * 
 * <pre>
 *   /api/{resource}
 *   /api/{resource}/{id}   (PUT, DELETE)
 * </pre>
 *
 * <h3>Query parameters</h3>
 * <table border="1">
 * <tr>
 * <th>Param</th>
 * <th>Values</th>
 * <th>Default</th>
 * </tr>
 * <tr>
 * <td>size</td>
 * <td>1,2,3,4,5,6,7,8,9,10,15,20</td>
 * <td>1</td>
 * </tr>
 * <tr>
 * <td>format</td>
 * <td>json | xml | text | html</td>
 * <td>json</td>
 * </tr>
 * </table>
 *
 * <h3>Example calls</h3>
 * 
 * <pre>
 *   GET  http://localhost:8080/api/orders?size=5&format=json
 *   GET  http://localhost:8080/api/orders?size=10&format=xml
 *   GET  http://localhost:8080/api/orders?size=3&format=text
 *   GET  http://localhost:8080/api/orders?size=5&format=html
 *   POST http://localhost:8080/api/users?size=2&format=json
 *   PUT  http://localhost:8080/api/users/42?size=3&format=xml
 *   DELETE http://localhost:8080/api/products/99?size=1&format=json
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class MockRestController {

    private final PayloadGeneratorService generator;

    public MockRestController(PayloadGeneratorService generator) {
        this.generator = generator;
    }

    // ─── GET ──────────────────────────────────────────────────────────────────

    /**
     * GET /api/{resource} — list resources
     */
    @GetMapping(value = "/{resource}", produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            MediaType.TEXT_XML_VALUE,
            MediaType.TEXT_PLAIN_VALUE,
            MediaType.TEXT_HTML_VALUE
    })
    public ResponseEntity<String> getCollection(
            @PathVariable String resource,
            @RequestParam(defaultValue = "1") int size,
            @RequestParam(defaultValue = "json") String format) {

        return buildResponse(size, format, "GET", resource);
    }

    /**
     * GET /api/{resource}/{id} — get single resource
     */
    @GetMapping(value = "/{resource}/{id}", produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            MediaType.TEXT_XML_VALUE,
            MediaType.TEXT_PLAIN_VALUE,
            MediaType.TEXT_HTML_VALUE
    })
    public ResponseEntity<String> getOne(
            @PathVariable String resource,
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int size,
            @RequestParam(defaultValue = "json") String format) {

        return buildResponse(size, format, "GET", resource + "/" + id);
    }

    // ─── POST ─────────────────────────────────────────────────────────────────

    /**
     * POST /api/{resource} — create resource
     */
    @PostMapping(value = "/{resource}", produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            MediaType.TEXT_XML_VALUE,
            MediaType.TEXT_PLAIN_VALUE,
            MediaType.TEXT_HTML_VALUE
    })
    public ResponseEntity<String> create(
            @PathVariable String resource,
            @RequestParam(defaultValue = "1") int size,
            @RequestParam(defaultValue = "json") String format,
            @RequestBody(required = false) String body) {

        return buildResponse(size, format, "POST", resource);
    }

    // ─── PUT ──────────────────────────────────────────────────────────────────

    /**
     * PUT /api/{resource}/{id} — update resource
     */
    @PutMapping(value = "/{resource}/{id}", produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            MediaType.TEXT_XML_VALUE,
            MediaType.TEXT_PLAIN_VALUE,
            MediaType.TEXT_HTML_VALUE
    })
    public ResponseEntity<String> update(
            @PathVariable String resource,
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int size,
            @RequestParam(defaultValue = "json") String format,
            @RequestBody(required = false) String body) {

        return buildResponse(size, format, "PUT", resource + "/" + id);
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    /**
     * DELETE /api/{resource}/{id} — delete resource
     */
    @DeleteMapping(value = "/{resource}/{id}", produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            MediaType.TEXT_XML_VALUE,
            MediaType.TEXT_PLAIN_VALUE,
            MediaType.TEXT_HTML_VALUE
    })
    public ResponseEntity<String> delete(
            @PathVariable String resource,
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int size,
            @RequestParam(defaultValue = "json") String format) {

        return buildResponse(size, format, "DELETE", resource + "/" + id);
    }

    // ─── Internal helper ──────────────────────────────────────────────────────

    private ResponseEntity<String> buildResponse(
            int size, String format, String method, String resource) {

        String payload = generator.generate(size, format, method, resource);
        MediaType mimeType;
        switch (format.toLowerCase()) {
            case "xml":  mimeType = MediaType.APPLICATION_XML;  break;
            case "text": mimeType = MediaType.TEXT_PLAIN;        break;
            case "html": mimeType = MediaType.TEXT_HTML;         break;
            default:     mimeType = MediaType.APPLICATION_JSON;  break;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mimeType);
        headers.set("X-Mock-Size-KB", String.valueOf(size));
        headers.set("X-Mock-Format", format.toLowerCase());
        headers.set("X-Mock-Method", method);
        headers.set("X-Mock-Resource", resource);
        headers.set("X-Payload-Bytes", String.valueOf(
                payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length));

        return ResponseEntity.ok().headers(headers).body(payload);
    }
}
