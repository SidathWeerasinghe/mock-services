package com.mockserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.mockserver.websocket.RawWebSocketHandler;

/**
 * Registers a raw (non-STOMP) WebSocket handler at /raw-ws.
 *
 * Useful for clients that speak plain WebSocket frames without STOMP.
 *
 * Connect via:
 *   ws://localhost:8080/raw-ws
 *   wss://localhost:8443/raw-ws
 *
 * Send a JSON request:
 * <pre>
 * {
 *   "size": 5,
 *   "format": "json",
 *   "method": "GET",
 *   "resource": "orders"
 * }
 * </pre>
 */
@Configuration
@EnableWebSocket
public class RawWebSocketConfig implements WebSocketConfigurer {

    private final RawWebSocketHandler rawWebSocketHandler;

    public RawWebSocketConfig(RawWebSocketHandler rawWebSocketHandler) {
        this.rawWebSocketHandler = rawWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
            .addHandler(rawWebSocketHandler, "/raw-ws")
            .setAllowedOriginPatterns("*");
    }
}
