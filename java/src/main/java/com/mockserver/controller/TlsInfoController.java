package com.mockserver.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reports the TLS session details negotiated for the current HTTP request.
 *
 * <h3>Usage</h3>
 * <pre>
 *   GET https://localhost:8442/tls-info   → TLS 1.2
 *   GET https://localhost:8443/tls-info   → TLS 1.3
 *   GET https://localhost:8444/tls-info   → TLS 1.2 or 1.3 (client decides)
 *   GET http://localhost:8080/tls-info    → no TLS (plain HTTP)
 * </pre>
 *
 * <p>Tomcat exposes the negotiated protocol via the
 * {@code javax.servlet.request.ssl_session_id} and related attributes set by
 * the JSSEImplementation. The simplest reliable attribute is
 * {@code javax.servlet.request.ssl_session} (an {@code SSLSession} object)
 * or the Tomcat-specific {@code org.apache.tomcat.util.net.SSLHostConfig}
 * attribute. We fall back to the request scheme for plain-HTTP requests.
 */
@RestController
public class TlsInfoController {

    /**
     * Returns TLS session info as JSON.
     */
    @GetMapping(value = "/tls-info", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> tlsInfo(HttpServletRequest request) {
        Map<String, Object> info = new LinkedHashMap<>();

        String scheme = request.getScheme();
        info.put("timestamp", Instant.now().toString());
        info.put("scheme", scheme);
        info.put("serverPort", request.getServerPort());
        info.put("remoteAddr", request.getRemoteAddr());

        if ("https".equalsIgnoreCase(scheme)) {
            // Tomcat sets these servlet attributes for TLS requests
            Object sslSession = request.getAttribute("javax.net.ssl.session");
            if (sslSession instanceof javax.net.ssl.SSLSession) {
                javax.net.ssl.SSLSession session = (javax.net.ssl.SSLSession) sslSession;
                info.put("negotiatedProtocol", session.getProtocol());
                info.put("cipherSuite", session.getCipherSuite());
                info.put("peerHost", session.getPeerHost());
                info.put("sessionId", bytesToHex(session.getId()));
            } else {
                // Fallback: Tomcat also exposes the protocol via a string attribute
                Object proto = request.getAttribute("javax.servlet.request.ssl_session_id");
                String tlsProtocol = resolveProtocolByPort(request.getServerPort());
                info.put("negotiatedProtocol", tlsProtocol);
                info.put("sessionId", proto != null ? proto.toString() : "n/a");
                info.put("cipherSuite",
                        getAttrString(request, "javax.servlet.request.cipher_suite"));
                info.put("note", "SSLSession attribute unavailable; port-based protocol inferred");
            }
            info.put("tlsEnabled", true);
        } else {
            info.put("tlsEnabled", false);
            info.put("negotiatedProtocol", "none (plain HTTP)");
            info.put("note", "Connect via https:// to test TLS");
        }

        return ResponseEntity.ok(info);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Infers the expected TLS version based on the listener port assignment
     * defined in application.properties / TomcatConfig.
     */
    private String resolveProtocolByPort(int port) {
        switch (port) {
            case 8442: return "TLSv1.2";
            case 8443: return "TLSv1.3";
            case 8444: return "TLSv1.2 or TLSv1.3";
            default:   return "unknown";
        }
    }

    private String getAttrString(HttpServletRequest req, String attr) {
        Object v = req.getAttribute(attr);
        return v != null ? v.toString() : "n/a";
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "n/a";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
