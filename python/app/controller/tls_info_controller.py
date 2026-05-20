"""
Reports the TLS session details negotiated for the current HTTP request.

Equivalent of TlsInfoController.java.
"""
from datetime import datetime, timezone

from flask import Blueprint, jsonify, request

tls_bp = Blueprint("tls", __name__)


def _resolve_protocol_by_port(port: int) -> str:
    mapping = {
        8442: "TLSv1.2",
        8443: "TLSv1.3",
        8444: "TLSv1.2 or TLSv1.3",
    }
    return mapping.get(port, "unknown")


def _bytes_to_hex(data) -> str:
    if not data:
        return "n/a"
    if isinstance(data, str):
        return data
    return "".join(f"{b:02x}" for b in data)


@tls_bp.route("/tls-info", methods=["GET"])
def tls_info():
    """Returns TLS session info as JSON."""
    try:
        server_port = int(request.environ.get("SERVER_PORT", 80))
    except (TypeError, ValueError):
        server_port = 80

    info = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "scheme": request.scheme,
        "serverPort": server_port,
        "remoteAddr": request.remote_addr,
    }

    if request.is_secure or request.scheme == "https":
        info["tlsEnabled"] = True

        # Gevent / WSGI may expose cipher and protocol
        negotiated = request.environ.get("SSL_PROTOCOL") or request.environ.get("HTTPS")
        cipher = request.environ.get("SSL_CIPHER")
        session_id = request.environ.get("SSL_SESSION_ID")

        if negotiated and negotiated is not True:
            info["negotiatedProtocol"] = str(negotiated)
            if cipher:
                info["cipherSuite"] = str(cipher)
            if session_id:
                info["sessionId"] = _bytes_to_hex(session_id)
        else:
            info["negotiatedProtocol"] = _resolve_protocol_by_port(server_port)
            info["note"] = "SSLSession attribute unavailable; port-based protocol inferred"
            if session_id:
                info["sessionId"] = _bytes_to_hex(session_id)
            elif request.environ.get("SSL_SESSION_ID") is not None:
                info["sessionId"] = str(request.environ.get("SSL_SESSION_ID", "n/a"))
    else:
        info["tlsEnabled"] = False
        info["negotiatedProtocol"] = "none (plain HTTP)"
        info["note"] = "Connect via https:// to test TLS"

    return jsonify(info)
