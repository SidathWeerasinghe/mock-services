package com.mockserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * Configures STOMP-over-WebSocket.
 *
 * Endpoints
 * ─────────
 *   ws://localhost:8080/ws      Plain WebSocket
 *   wss://localhost:8443/ws     Secure WebSocket
 *
 * STOMP destinations
 * ──────────────────
 *   /app/mock          → controller @MessageMapping("/mock")
 *   /topic/response    → broker broadcasts responses here
 *
 * SockJS fallback is enabled for browsers that block raw WS.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Client subscribes to /topic/...
        registry.enableSimpleBroker("/topic");
        // Client sends messages to /app/...
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
            .addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS(); // SockJS fallback
    }
}
