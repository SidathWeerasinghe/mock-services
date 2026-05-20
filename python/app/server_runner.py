"""
Multi-port server runner — mirrors TomcatConfig.java port map.

  8080 — plain HTTP / WS  (no TLS)
  8442 — HTTPS / WSS, TLS 1.2 only
  8443 — HTTPS / WSS, TLS 1.3 only  (primary)
  8444 — HTTPS / WSS, TLS 1.2 + 1.3 (combined)

Set MOCK_SSL_ENABLED=false to run HTTP-only on PORT (default 8080).
"""
import logging
import os
import ssl
import threading

from gevent import pywsgi
from geventwebsocket.handler import WebSocketHandler

from app.cert_utils import ensure_certs_exist

logger = logging.getLogger(__name__)

HTTP_PORT = int(os.environ.get("MOCK_HTTP_PORT", "8080"))
TLS12_PORT = int(os.environ.get("MOCK_TLS12_PORT", "8442"))
TLS13_PORT = int(os.environ.get("MOCK_TLS13_PORT", "8443"))
TLS1213_PORT = int(os.environ.get("MOCK_TLS12_13_PORT", "8444"))
SSL_ENABLED = os.environ.get("MOCK_SSL_ENABLED", "true").lower() in ("1", "true", "yes")


def _make_ssl_context(min_version: ssl.TLSVersion, max_version: ssl.TLSVersion) -> ssl.SSLContext:
    cert_file, key_file = ensure_certs_exist()
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.load_cert_chain(certfile=cert_file, keyfile=key_file)
    ctx.minimum_version = min_version
    ctx.maximum_version = max_version
    return ctx


def _serve(app, port: int, ssl_context=None, label: str = ""):
    logger.info("Listening on %s%s", f"https://0.0.0.0:{port}" if ssl_context else f"http://0.0.0.0:{port}", label)
    server = pywsgi.WSGIServer(
        ("0.0.0.0", port),
        app,
        handler_class=WebSocketHandler,
        ssl_context=ssl_context,
        log=None,
    )
    server.serve_forever()


def run_servers(app):
    """Start HTTP and optional multi-TLS listeners in background threads."""
    if not SSL_ENABLED:
        port = int(os.environ.get("PORT", HTTP_PORT))
        logger.info("Mock API Server (Python) — HTTP only on port %s", port)
        _serve(app, port)
        return

    try:
        ctx12 = _make_ssl_context(ssl.TLSVersion.TLSv1_2, ssl.TLSVersion.TLSv1_2)
        ctx13 = _make_ssl_context(ssl.TLSVersion.TLSv1_3, ssl.TLSVersion.TLSv1_3)
        ctx_both = _make_ssl_context(ssl.TLSVersion.TLSv1_2, ssl.TLSVersion.TLSv1_3)

        threads = []
        for port, ctx, label in [
            (HTTP_PORT, None, " (HTTP)"),
            (TLS12_PORT, ctx12, " (TLS 1.2)"),
            (TLS13_PORT, ctx13, " (TLS 1.3)"),
            (TLS1213_PORT, ctx_both, " (TLS 1.2+1.3)"),
        ]:
            t = threading.Thread(
                target=_serve,
                args=(app, port, ctx, label),
                daemon=True,
                name=f"server-{port}",
            )
            t.start()
            threads.append(t)

        logger.info(
            "Mock API Server (Python) — ports %s (HTTP), %s (TLS1.2), %s (TLS1.3), %s (TLS1.2+1.3)",
            HTTP_PORT, TLS12_PORT, TLS13_PORT, TLS1213_PORT,
        )
        for t in threads:
            t.join()
    except Exception as e:
        logger.warning("SSL setup failed (%s) — falling back to HTTP on %s", e, HTTP_PORT)
        _serve(app, HTTP_PORT)
