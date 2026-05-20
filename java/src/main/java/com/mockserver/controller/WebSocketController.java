package com.mockserver.controller;

import com.mockserver.service.PayloadGeneratorService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * STOMP WebSocket controller.
 *
 * <h3>Client sends to</h3>
 * <pre>/app/mock</pre>
 *
 * <h3>Server replies on</h3>
 * <pre>/topic/response</pre>
 *
 * <h3>Request payload (JSON)</h3>
 * <pre>
 * {
 *   "size":     5,
 *   "format":   "json",
 *   "method":   "GET",
 *   "resource": "orders"
 * }
 * </pre>
 *
 * <h3>Connect via STOMP / SockJS</h3>
 * <pre>
 *   ws://localhost:8080/ws/websocket      (WS)
 *   wss://localhost:8443/ws/websocket     (WSS)
 * </pre>
 */
@Controller
public class WebSocketController {

    private final PayloadGeneratorService generator;

    public WebSocketController(PayloadGeneratorService generator) {
        this.generator = generator;
    }

    /**
     * Handles STOMP messages sent to {@code /app/mock}.
     * Broadcasts the generated payload to all subscribers of {@code /topic/response}.
     *
     * @param request Map with keys: size (int), format (string), method (string), resource (string)
     * @return Generated mock payload string
     */
    @MessageMapping("/mock")
    @SendTo("/topic/response")
    public String handleMockRequest(Map<String, Object> request) {
        int    size     = toInt(request.getOrDefault("size", 1));
        String format   = (String) request.getOrDefault("format",   "json");
        String method   = (String) request.getOrDefault("method",   "GET");
        String resource = (String) request.getOrDefault("resource", "items");

        return generator.generate(size, format, method, resource);
    }

    // ──────────────────────────────────────────────────────────────────────────

    private int toInt(Object val) {
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Number)  return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); }
        catch (NumberFormatException e) { return 1; }
    }
}
