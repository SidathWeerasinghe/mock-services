"""
Self-signed certificate generator for TLS/WSS support.

Equivalent of KeystoreInitializer.java + generate-keystore.bat.
Generates a self-signed PEM certificate + key at startup if they don't exist.
"""
import os
import subprocess
import logging

logger = logging.getLogger(__name__)

CERT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "certs")
CERT_FILE = os.path.join(CERT_DIR, "server.crt")
KEY_FILE = os.path.join(CERT_DIR, "server.key")


def ensure_certs_exist():
    """Generate self-signed certs if they don't already exist."""
    if os.path.exists(CERT_FILE) and os.path.exists(KEY_FILE):
        logger.info("[Certs] %s already exists — skipping generation.", CERT_FILE)
        return CERT_FILE, KEY_FILE

    os.makedirs(CERT_DIR, exist_ok=True)
    logger.info("[Certs] Generating self-signed certificate at %s ...", CERT_DIR)

    try:
        subprocess.run([
            "openssl", "req",
            "-x509",
            "-newkey", "rsa:2048",
            "-keyout", KEY_FILE,
            "-out", CERT_FILE,
            "-days", "3650",
            "-nodes",
            "-subj", "/CN=localhost/OU=Dev/O=MockServer/L=City/ST=State/C=US",
        ], check=True, capture_output=True)
        logger.info("[Certs] Self-signed certificate created at %s", CERT_DIR)
    except FileNotFoundError:
        logger.warning("[Certs] openssl not found on PATH. TLS will not work.")
    except subprocess.CalledProcessError as e:
        logger.warning("[Certs] openssl failed: %s. TLS may not work.", e.stderr)

    return CERT_FILE, KEY_FILE
