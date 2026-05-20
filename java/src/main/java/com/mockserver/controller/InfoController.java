package com.mockserver.controller;

import com.mockserver.service.PayloadGeneratorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Exposes a health/info endpoint and a sizes reference endpoint.
 */
@RestController
public class InfoController {

    private final PayloadGeneratorService generator;

    public InfoController(PayloadGeneratorService generator) {
        this.generator = generator;
    }

    /**
     * GET /health — liveness probe.
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");
        info.put("server", "MockAPIServer/1.0");
        info.put("timestamp", java.time.Instant.now().toString());
        return info;
    }

    /**
     * GET /info — lists all available endpoints and options.
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("server", "MockAPIServer/1.0");
        result.put("validSizes", PayloadGeneratorService.VALID_SIZES_KB);
        result.put("formats", Arrays.asList("json", "xml"));
        result.put("methods", Arrays.asList("GET", "POST", "PUT", "DELETE"));

        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("REST (HTTP)", "http://localhost:8080/api/{resource}?size={kb}&format={json|xml}");
        endpoints.put("REST (HTTPS)", "https://localhost:8443/api/{resource}?size={kb}&format={json|xml}");
        endpoints.put("WebSocket (WS)", "ws://localhost:8080/ws");
        endpoints.put("WebSocket (WSS)", "wss://localhost:8443/ws");
        endpoints.put("Raw WS", "ws://localhost:8080/raw-ws");
        endpoints.put("Raw WSS", "wss://localhost:8443/raw-ws");
        endpoints.put("GraphQL HTTP", "http://localhost:8080/graphql  (POST — Query & Mutation)");
        endpoints.put("GraphiQL IDE", "http://localhost:8080/graphiql");
        endpoints.put("GraphQL WS", "ws://localhost:8080/graphql-ws   (Subscription)");
        endpoints.put("GraphQL WSS", "wss://localhost:8443/graphql-ws  (Subscription)");
        endpoints.put("Test UI", "http://localhost:8080/index.html");
        result.put("endpoints", endpoints);

        Map<String, Object> examples = new LinkedHashMap<>();
        examples.put("GET 5KB JSON", "GET /api/orders?size=5&format=json");
        examples.put("GET 10KB XML", "GET /api/orders?size=10&format=xml");
        examples.put("POST 2KB JSON", "POST /api/users?size=2&format=json");
        examples.put("PUT 3KB XML", "PUT /api/users/42?size=3&format=xml");
        examples.put("DELETE 1KB JSON", "DELETE /api/products/99?size=1&format=json");
        examples.put("GraphQL Query",
                "POST /graphql  {\"query\":\"{ mock(resource:\\\"orders\\\", size:5) { sizeKb byteLength } }\"}");
        examples.put("GraphQL Mutation",
                "POST /graphql  {\"query\":\"mutation { create(resource:\\\"users\\\", size:2) { success affectedId } }\"}");
        examples.put("GraphQL Subscribe",
                "WS  /graphql-ws  subscription { mockStream(resource:\\\"events\\\", size:3, intervalMs:2000) { timestamp payload } }");
        result.put("examples", examples);

        return result;
    }
}
