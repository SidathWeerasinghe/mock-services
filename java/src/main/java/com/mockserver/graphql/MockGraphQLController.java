package com.mockserver.graphql;

import com.mockserver.service.PayloadGeneratorService;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * GraphQL resolver for all Query, Mutation, and Subscription operations.
 *
 * <h3>Endpoints</h3>
 * 
 * <pre>
 *   HTTP POST  /graphql                         ← Queries &amp; Mutations
 *   GET        /graphiql                         ← In-browser IDE
 *   WebSocket  ws://localhost:8080/graphql-ws    ← Subscriptions (WS)
 *   WebSocket  wss://localhost:8443/graphql-ws   ← Subscriptions (WSS)
 * </pre>
 *
 * <h3>Example Query</h3>
 * 
 * <pre>
 * query {
 *   mock(resource: "orders", size: 5, format: "json") {
 *     requestId sizeKb byteLength timestamp payload
 *   }
 * }
 * </pre>
 *
 * <h3>Example Mutation</h3>
 * 
 * <pre>
 * mutation {
 *   create(resource: "users", size: 2, format: "json") {
 *     success operation affectedId
 *     response { sizeKb byteLength }
 *   }
 * }
 * </pre>
 *
 * <h3>Example Subscription</h3>
 * 
 * <pre>
 * subscription {
 *   mockStream(resource: "events", size: 3, format: "json", intervalMs: 2000) {
 *     requestId timestamp sizeKb byteLength payload
 *   }
 * }
 * </pre>
 */
@Controller
public class MockGraphQLController {

    /** Minimum allowed subscription interval to prevent runaway clients. */
    private static final long MIN_INTERVAL_MS = 500L;

    private final PayloadGeneratorService generator;

    public MockGraphQLController(PayloadGeneratorService generator) {
        this.generator = generator;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Resolves: {@code query { mock(...) }}
     */
    @QueryMapping
    public MockGraphQLResponse mock(
            @Argument String resource,
            @Argument(name = "size") Integer size,
            @Argument(name = "format") String format,
            @Argument(name = "method") String method) {

        int sz = (size != null) ? size : 1;
        String fmt = (format != null) ? format : "json";
        String mtd = (method != null) ? method : "GET";

        String payload = generator.generate(sz, fmt, mtd, resource);
        return new MockGraphQLResponse(mtd, resource, sz, fmt, payload);
    }

    /**
     * Resolves: {@code query { health }}
     */
    @QueryMapping
    public Map<String, Object> health() {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("status", "UP");
        h.put("server", "MockAPIServer/1.0");
        h.put("timestamp", Instant.now().toString());
        return h;
    }

    /**
     * Resolves: {@code query { info }}
     */
    @QueryMapping
    public Map<String, Object> info() {
        Map<String, Object> i = new LinkedHashMap<>();
        i.put("server", "MockAPIServer/1.0");
        i.put("validSizes", PayloadGeneratorService.VALID_SIZES_KB);
        i.put("formats", Arrays.asList("json", "xml", "text", "html"));
        i.put("methods", Arrays.asList("GET", "POST", "PUT", "DELETE"));
        i.put("httpEndpoint", "http://localhost:8080/graphql");
        i.put("graphiqlUrl", "http://localhost:8080/graphiql");
        i.put("wsSubscriptionUrl", "ws://localhost:8080/graphql-ws");
        i.put("wssSubscriptionUrl", "wss://localhost:8443/graphql-ws");
        return i;
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Resolves: {@code mutation { create(...) }}
     */
    @MutationMapping
    public MutationResult create(
            @Argument String resource,
            @Argument Integer size,
            @Argument String format) {

        int sz = (size != null) ? size : 1;
        String fmt = (format != null) ? format : "json";
        String payload = generator.generate(sz, fmt, "POST", resource);
        String newId = UUID.randomUUID().toString();

        return new MutationResult(
                true, "CREATE", newId,
                new MockGraphQLResponse("POST", resource, sz, fmt, payload));
    }

    /**
     * Resolves: {@code mutation { update(...) }}
     */
    @MutationMapping
    public MutationResult update(
            @Argument String resource,
            @Argument String id,
            @Argument Integer size,
            @Argument String format) {

        int sz = (size != null) ? size : 1;
        String fmt = (format != null) ? format : "json";
        String payload = generator.generate(sz, fmt, "PUT", resource + "/" + id);

        return new MutationResult(
                true, "UPDATE", id,
                new MockGraphQLResponse("PUT", resource + "/" + id, sz, fmt, payload));
    }

    /**
     * Resolves: {@code mutation { delete(...) }}
     */
    @MutationMapping
    public MutationResult delete(
            @Argument String resource,
            @Argument String id,
            @Argument Integer size,
            @Argument String format) {

        int sz = (size != null) ? size : 1;
        String fmt = (format != null) ? format : "json";
        String payload = generator.generate(sz, fmt, "DELETE", resource + "/" + id);

        return new MutationResult(
                true, "DELETE", id,
                new MockGraphQLResponse("DELETE", resource + "/" + id, sz, fmt, payload));
    }

    // ── Subscriptions ─────────────────────────────────────────────────────────

    /**
     * Resolves: {@code subscription { mockStream(...) }}
     *
     * <p>
     * Emits a new mock payload every {@code intervalMs} milliseconds over
     * WebSocket.
     * Clients connect to:
     * <ul>
     * <li>{@code ws://localhost:8080/graphql-ws} — plain WS</li>
     * <li>{@code wss://localhost:8443/graphql-ws} — secure WSS</li>
     * </ul>
     */
    @SubscriptionMapping
    public Flux<MockGraphQLResponse> mockStream(
            @Argument String resource,
            @Argument Integer size,
            @Argument String format,
            @Argument String method,
            @Argument Integer intervalMs) {

        int sz = (size != null) ? size : 1;
        String fmt = (format != null) ? format : "json";
        String mtd = (method != null) ? method : "GET";
        long interval = Math.max(MIN_INTERVAL_MS,
                (intervalMs != null) ? intervalMs.longValue() : 1000L);

        return Flux
                .interval(Duration.ofMillis(interval))
                .map(tick -> {
                    String payload = generator.generate(sz, fmt, mtd, resource);
                    return new MockGraphQLResponse(mtd, resource, sz, fmt, payload);
                });
    }
}
