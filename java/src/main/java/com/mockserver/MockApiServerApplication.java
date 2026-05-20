package com.mockserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Mock API Server.
 *
 * Exposes:
 *   - REST  (HTTP)       : http://localhost:8080/api/{resource}?size={kb}&format={json|xml|text|html}
 *   - REST  (HTTPS)      : https://localhost:8443/api/{resource}
 *   - WS                 : ws://localhost:8080/ws         (STOMP/SockJS)
 *   - WSS                : wss://localhost:8443/ws        (STOMP/SockJS)
 *   - Raw WS             : ws://localhost:8080/raw-ws
 *   - Raw WSS            : wss://localhost:8443/raw-ws
 *   - GraphQL HTTP       : http://localhost:8080/graphql  (Query + Mutation)
 *   - GraphiQL IDE       : http://localhost:8080/graphiql
 *   - GraphQL WS         : ws://localhost:8080/graphql-ws  (Subscription)
 *   - GraphQL WSS        : wss://localhost:8443/graphql-ws (Subscription)
 *
 * TLS version test endpoints (/tls-info):
 *   - https://localhost:8442/tls-info  → TLS 1.2 only
 *   - https://localhost:8443/tls-info  → TLS 1.3 only  (primary)
 *   - https://localhost:8444/tls-info  → TLS 1.2 + 1.3 (combined)
 *
 * All payload endpoints accept:
 *   size   : 1,2,3,4,5,6,7,8,9,10,15,20 (KB)
 *   format : json | xml | text | html
 */
@SpringBootApplication
public class MockApiServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockApiServerApplication.class, args);
    }
}
