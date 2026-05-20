package com.mockserver.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;

/**
 * Configures Tomcat connectors for multi-TLS-version testing.
 *
 * <h3>Port map (when SSL is enabled)</h3>
 * <ul>
 *   <li>8080 — plain HTTP / WS  (no TLS)</li>
 *   <li>8442 — HTTPS / WSS, TLS 1.2 only</li>
 *   <li>8443 — HTTPS / WSS, TLS 1.3 only  (primary Spring Boot connector)</li>
 *   <li>8444 — HTTPS / WSS, TLS 1.2 + 1.3 (combined / browser-friendly)</li>
 * </ul>
 *
 * <p>IMPORTANT: Tomcat's native SSL code does not understand Spring's
 * {@code classpath:} URI prefix. The keystore path is resolved to an absolute
 * filesystem path via {@link ResourceLoader} before being passed to the connector.
 */
@Configuration
public class TomcatConfig {

    @Value("${mock.server.http-port:8080}")
    private int httpPort;

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    // Raw property value — may be "classpath:keystore.p12" or a file path
    @Value("${server.ssl.key-store:classpath:keystore.p12}")
    private String keyStoreLocation;

    @Value("${server.ssl.key-store-password:changeit}")
    private String keyStorePassword;

    @Value("${server.ssl.key-store-type:PKCS12}")
    private String keyStoreType;

    @Value("${server.ssl.key-alias:mock-server}")
    private String keyAlias;

    @Value("${mock.server.tls12-port:8442}")
    private int tls12Port;

    @Value("${mock.server.tls12-13-port:8444}")
    private int tls1213Port;

    /** Used to resolve "classpath:" locations to actual filesystem paths. */
    private final ResourceLoader resourceLoader;

    public TomcatConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> additionalConnectors() {
        return factory -> {
            if (!sslEnabled) return;

            // Resolve the keystore to a real filesystem path that Tomcat can open
            String resolvedKeyStore = resolveKeystorePath(keyStoreLocation);

            // ── Plain HTTP on 8080 ────────────────────────────────────────────
            Connector http = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            http.setScheme("http");
            http.setPort(httpPort);
            http.setSecure(false);
            factory.addAdditionalTomcatConnectors(http);

            // ── TLS 1.2 only on 8442 ─────────────────────────────────────────
            factory.addAdditionalTomcatConnectors(
                    buildTlsConnector(tls12Port, "TLSv1.2", "TLSv1.2", resolvedKeyStore));

            // ── TLS 1.2 + 1.3 combined on 8444 ───────────────────────────────
            factory.addAdditionalTomcatConnectors(
                    buildTlsConnector(tls1213Port, "TLSv1.2", "TLSv1.2,TLSv1.3", resolvedKeyStore));
        };
    }

    /**
     * Resolves a Spring resource location (e.g. {@code classpath:keystore.p12})
     * to an absolute filesystem path string that Tomcat's SSL layer can consume.
     *
     * <p>If the resource cannot be unwrapped to a {@link java.io.File} (e.g. inside
     * a fat JAR), the raw location is returned as-is as a last resort.
     */
    private String resolveKeystorePath(String location) {
        try {
            Resource resource = resourceLoader.getResource(location);
            if (resource.exists()) {
                return resource.getFile().getAbsolutePath();
            }
        } catch (IOException ignored) {
            // Fallthrough to raw-value fallback
        }
        // If not a classpath: prefix, return as-is (already a file path)
        return location.startsWith("classpath:") ? location.substring("classpath:".length()) : location;
    }

    /**
     * Creates an HTTPS Connector that is restricted to specific TLS protocol(s).
     *
     * @param port             TCP port to listen on
     * @param sslProtocol      Value for {@code SSLProtocol} (the default/min protocol)
     * @param enabledProtocols Comma-separated list for {@code SSLEnabledProtocols}
     * @param keystorePath     Absolute filesystem path to the keystore file
     */
    private Connector buildTlsConnector(int port, String sslProtocol,
                                         String enabledProtocols, String keystorePath) {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme("https");
        connector.setSecure(true);
        connector.setPort(port);

        Http11NioProtocol proto = (Http11NioProtocol) connector.getProtocolHandler();
        proto.setSSLEnabled(true);
        proto.setKeystoreFile(keystorePath);
        proto.setKeystorePass(keyStorePassword);
        proto.setKeystoreType(keyStoreType);
        proto.setKeyAlias(keyAlias);
        proto.setSSLProtocol(sslProtocol);
        // setProperty is the portable way to set SSLEnabledProtocols on Tomcat 9/10
        connector.setProperty("SSLEnabledProtocols", enabledProtocols);

        return connector;
    }
}

