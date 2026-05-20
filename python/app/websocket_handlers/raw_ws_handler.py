"""
Raw (non-STOMP) WebSocket handler for the /raw-ws endpoint.

Protocol:
  Client sends a UTF-8 JSON text frame:
  {
    "size":     5,
    "format":   "json",
    "method":   "GET",
    "resource": "orders"
  }

  Server replies with a single text frame containing the generated payload.

Connect via:
  ws://localhost:8080/raw-ws       WS  (plain)
  wss://localhost:8443/raw-ws      WSS (TLS)

Equivalent of RawWebSocketHandler.java.
"""
import json

from app.service.payload_generator import generate, VALID_SIZES_KB
from app.websocket_handlers.ws import sock


def init_websocket(app):
    """WebSocket routes are registered via @sock.route decorators."""


@sock.route("/raw-ws")
def raw_ws(ws):
    """Handle raw WebSocket connections at /raw-ws."""
    session_id = id(ws)
    welcome = json.dumps({
        "event": "connected",
        "sessionId": str(session_id),
        "remoteAddr": "unknown",
        "validSizes": VALID_SIZES_KB,
        "formats": ["json", "xml"],
        "methods": ["GET", "POST", "PUT", "DELETE"],
        "usage": 'Send JSON: {"size":5,"format":"json","method":"GET","resource":"orders"}',
    })
    ws.send(welcome)

    while True:
        data = ws.receive()
        if data is None:
            break

        try:
            req = json.loads(data)
            size = int(req.get("size", 1))
            fmt = str(req.get("format", "json"))
            method = str(req.get("method", "GET"))
            resource = str(req.get("resource", "items"))

            payload = generate(size, fmt, method, resource)
            ws.send(payload)

        except ValueError as e:
            err = json.dumps({
                "error": str(e),
                "validSizes": VALID_SIZES_KB,
            })
            ws.send(err)
        except Exception as e:
            err = json.dumps({
                "error": f"Internal error: {str(e)}",
            })
            ws.send(err)
