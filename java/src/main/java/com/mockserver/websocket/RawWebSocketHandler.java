package com.mockserver.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockserver.service.PayloadGeneratorService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * Raw (non-STOMP) WebSocket handler for the {@code /raw-ws} endpoint.
 *
 * <h3>Protocol</h3>
 * <p>Client sends a UTF-8 JSON text frame:
 * <pre>
 * {
 *   "size":     5,
 *   "format":   "json",
 *   "method":   "GET",
 *   "resource": "orders"
 * }
 * </pre>
 *
 * <p>Server replies with a single text frame containing the generated payload.
 *
 * <h3>Connect via</h3>
 * <pre>
 *   ws://localhost:8080/raw-ws       WS  (plain)
 *   wss://localhost:8443/raw-ws      WSS (TLS)
 * </pre>
 *
 * <h3>Error response</h3>
 * <pre>
 * { "error": "...", "validSizes": [1,2,3,4,5,6,7,8,9,10,15,20] }
 * </pre>
 */
@Component
public class RawWebSocketHandler extends TextWebSocketHandler {

    private final PayloadGeneratorService generator;
    private final ObjectMapper            objectMapper;

    public RawWebSocketHandler(PayloadGeneratorService generator,
                               ObjectMapper objectMapper) {
        this.generator    = generator;
        this.objectMapper = objectMapper;
    }

    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String welcome = objectMapper.writeValueAsString(Map.of(
            "event",       "connected",
            "sessionId",   session.getId(),
            "remoteAddr",  String.valueOf(session.getRemoteAddress()),
            "validSizes",  PayloadGeneratorService.VALID_SIZES_KB,
            "formats",     new String[]{"json", "xml"},
            "methods",     new String[]{"GET", "POST", "PUT", "DELETE"},
            "usage",       "Send JSON: {\"size\":5,\"format\":\"json\",\"method\":\"GET\",\"resource\":\"orders\"}"
        ));
        session.sendMessage(new TextMessage(welcome));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = objectMapper.readValue(message.getPayload(), Map.class);

            int    size     = toInt(req.getOrDefault("size",     1));
            String format   = (String) req.getOrDefault("format",   "json");
            String method   = (String) req.getOrDefault("method",   "GET");
            String resource = (String) req.getOrDefault("resource", "items");

            String payload = generator.generate(size, format, method, resource);
            session.sendMessage(new TextMessage(payload));

        } catch (IllegalArgumentException e) {
            // Invalid size or format
            String err = objectMapper.writeValueAsString(Map.of(
                "error",      e.getMessage(),
                "validSizes", PayloadGeneratorService.VALID_SIZES_KB
            ));
            session.sendMessage(new TextMessage(err));
        } catch (Exception e) {
            String err = objectMapper.writeValueAsString(Map.of(
                "error", "Internal error: " + e.getMessage()
            ));
            session.sendMessage(new TextMessage(err));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.printf("[RawWS] Session %s closed: %s%n", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.printf("[RawWS] Transport error on %s: %s%n",
            session.getId(), exception.getMessage());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────

    private int toInt(Object val) {
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Number)  return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return 1; }
    }
}
