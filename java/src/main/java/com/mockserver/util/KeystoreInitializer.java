package com.mockserver.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;

/**
 * Generates a self-signed PKCS12 keystore at startup if it doesn't exist.
 *
 * <p>The keystore is placed under {@code src/main/resources/keystore.p12}
 * and is used for WSS/HTTPS on port 8443.
 *
 * <p>Requires the {@code keytool} command to be on the system PATH
 * (bundled with any JDK installation).
 *
 * <p>In production, replace this with a CA-signed certificate.
 */
@Component
public class KeystoreInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KeystoreInitializer.class);

    @Value("${server.ssl.key-store:classpath:keystore.p12}")
    private String keystorePath;

    @Value("${server.ssl.key-store-password:changeit}")
    private String keystorePassword;

    @Value("${server.ssl.key-alias:mock-server}")
    private String keyAlias;

    @Override
    public void run(ApplicationArguments args) {
        // Only generate if the path is classpath: — file: paths are handled separately
        if (!keystorePath.startsWith("classpath:")) return;

        String filename = keystorePath.replace("classpath:", "");
        Path resourceDir = Paths.get("src", "main", "resources");
        Path dest = resourceDir.resolve(filename);

        if (Files.exists(dest)) {
            log.info("[Keystore] {} already exists — skipping generation.", dest);
            return;
        }

        log.info("[Keystore] Generating self-signed keystore at {} ...", dest);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "keytool",
                "-genkeypair",
                "-alias",     keyAlias,
                "-keyalg",    "RSA",
                "-keysize",   "2048",
                "-storetype", "PKCS12",
                "-keystore",  dest.toAbsolutePath().toString(),
                "-validity",  "3650",
                "-storepass", keystorePassword,
                "-keypass",   keystorePassword,
                "-dname",     "CN=localhost, OU=Dev, O=MockServer, L=City, ST=State, C=US",
                "-noprompt"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                reader.lines().forEach(line -> log.debug("[keytool] {}", line));
            }

            int exit = process.waitFor();
            if (exit == 0) {
                log.info("[Keystore] Self-signed keystore created at {}", dest);
            } else {
                log.warn("[Keystore] keytool exited with code {}. SSL may not work.", exit);
            }
        } catch (Exception e) {
            log.warn("[Keystore] Could not generate keystore: {}. WSS disabled.", e.getMessage());
        }
    }
}
