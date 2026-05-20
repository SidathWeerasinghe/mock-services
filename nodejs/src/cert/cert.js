'use strict';

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

function ensureCerts(certDir) {
  fs.mkdirSync(certDir, { recursive: true });
  const certFile = path.join(certDir, 'server.crt');
  const keyFile = path.join(certDir, 'server.key');

  if (fs.existsSync(certFile) && fs.existsSync(keyFile)) {
    return { certFile, keyFile };
  }

  try {
    execSync(
      `openssl req -x509 -newkey rsa:2048 -keyout "${keyFile}" -out "${certFile}" -days 3650 -nodes -subj "/CN=localhost/OU=Dev/O=MockServer/L=City/ST=State/C=US"`,
      { stdio: 'pipe' }
    );
    console.log(`[Certs] Self-signed certificate created at ${certDir}`);
  } catch (e) {
    throw new Error(`Could not generate certificates (openssl required): ${e.message}`);
  }

  return { certFile, keyFile };
}

module.exports = { ensureCerts };
