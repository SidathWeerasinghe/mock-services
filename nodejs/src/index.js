'use strict';

/**
 * Mock API Server (Node.js) — same endpoints as Java, Python, and Go.
 *
 *   REST:     /api/{resource}?size={kb}&format={json|xml|text|html}
 *   STOMP:    /ws (SockJS)
 *   Raw WS:   /raw-ws
 *   GraphQL:  POST /graphql, GET /graphiql
 *   GraphQL:  /graphql-ws (subscriptions)
 *   TLS:      /tls-info on 8080, 8442, 8443, 8444
 */
const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const { createApp } = require('./app');
const { ensureCerts } = require('./cert/cert');
const { attachRawWs } = require('./ws/raw');
const { attachGraphqlWs } = require('./ws/graphqlWs');
const { attachStompWs } = require('./ws/stomp');

function envInt(key, def) {
  const v = process.env[key];
  if (v) {
    const n = parseInt(v, 10);
    if (!Number.isNaN(n)) return n;
  }
  return def;
}

function envBool(key, def) {
  const v = process.env[key];
  if (!v) return def;
  return v === '1' || v.toLowerCase() === 'true' || v.toLowerCase() === 'yes';
}

function resolveStaticDir() {
  const candidates = [
    process.env.MOCK_STATIC_DIR,
    path.join(__dirname, '..', 'static'),
    path.join(process.cwd(), 'static'),
  ].filter(Boolean);
  for (const p of candidates) {
    if (fs.existsSync(path.join(p, 'index.html'))) return p;
  }
  return path.join(__dirname, '..', 'static');
}

function attachWebSockets(server) {
  attachRawWs(server);
  attachGraphqlWs(server);
  attachStompWs(server);
}

function listenHttp(app, port, label) {
  return new Promise((resolve, reject) => {
    const server = http.createServer(app);
    attachWebSockets(server);
    server.listen(port, '0.0.0.0', () => {
      console.log(`Listening on http://0.0.0.0:${port} (${label})`);
      resolve(server);
    });
    server.on('error', reject);
  });
}

function listenTls(app, port, certFile, keyFile, minVersion, maxVersion, label) {
  return new Promise((resolve, reject) => {
    const options = {
      key: fs.readFileSync(keyFile),
      cert: fs.readFileSync(certFile),
      minVersion,
      maxVersion,
    };
    const server = https.createServer(options, app);
    attachWebSockets(server);
    server.listen(port, '0.0.0.0', () => {
      console.log(`Listening on https://0.0.0.0:${port} (${label})`);
      resolve(server);
    });
    server.on('error', reject);
  });
}

async function main() {
  const staticDir = resolveStaticDir();
  const app = createApp(staticDir);

  const httpPort = envInt('MOCK_HTTP_PORT', envInt('PORT', 8080));
  const tls12Port = envInt('MOCK_TLS12_PORT', 8442);
  const tls13Port = envInt('MOCK_TLS13_PORT', 8443);
  const tls1213Port = envInt('MOCK_TLS12_13_PORT', 8444);
  const sslEnabled = envBool('MOCK_SSL_ENABLED', true);
  const certDir = process.env.MOCK_CERT_DIR || path.join(__dirname, '..', 'certs');

  console.log('Mock API Server (Node.js) initialized');

  if (!sslEnabled) {
    await listenHttp(app, httpPort, 'HTTP only');
    return;
  }

  try {
    const { certFile, keyFile } = ensureCerts(certDir);
    await Promise.all([
      listenHttp(app, httpPort, 'HTTP'),
      listenTls(app, tls12Port, certFile, keyFile, 'TLSv1.2', 'TLSv1.2', 'TLS 1.2'),
      listenTls(app, tls13Port, certFile, keyFile, 'TLSv1.3', 'TLSv1.3', 'TLS 1.3'),
      listenTls(app, tls1213Port, certFile, keyFile, 'TLSv1.2', 'TLSv1.3', 'TLS 1.2+1.3'),
    ]);
    console.log(
      `Mock API Server (Node.js) — ports ${httpPort} (HTTP), ${tls12Port} (TLS1.2), ${tls13Port} (TLS1.3), ${tls1213Port} (TLS1.2+1.3)`
    );
  } catch (err) {
    console.warn(`SSL setup failed (${err.message}) — falling back to HTTP on ${httpPort}`);
    await listenHttp(app, httpPort, 'HTTP fallback');
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
