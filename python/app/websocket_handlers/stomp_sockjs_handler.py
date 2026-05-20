"""
STOMP-over-SockJS WebSocket handler for /ws endpoint.

Equivalent of WebSocketConfig.java + WebSocketController.java.

Connect via SockJS:
  http://localhost:8080/ws  → ws://localhost:8080/ws/{server}/{session}/websocket

STOMP destinations:
  /app/mock          → generate payload (client SEND)
  /topic/response    → server MESSAGE broadcast
"""
import json
import logging
import random
from flask import Blueprint, jsonify

from app.service.payload_generator import generate, VALID_SIZES_KB
from app.websocket_handlers.ws import sock

logger = logging.getLogger(__name__)

stomp_bp = Blueprint("stomp", __name__)

STOMP_NULL = "\x00"


def init_stomp(app):
    app.register_blueprint(stomp_bp)


@stomp_bp.route("/ws/info")
def sockjs_info():
    """SockJS info endpoint — required before WebSocket upgrade."""
    return jsonify({
        "websocket": True,
        "cookie_needed": False,
        "origins": ["*:*"],
        "entropy": random.randint(1_000_000_000, 9_999_999_999),
    })


def _parse_stomp_frame(data: str) -> tuple:
    """Parse a STOMP frame into (command, headers, body)."""
    data = data.rstrip(STOMP_NULL)
    parts = data.split("\n\n", 1)
    head = parts[0]
    body = parts[1] if len(parts) > 1 else ""
    lines = head.split("\n")
    command = lines[0].strip()
    headers = {}
    for line in lines[1:]:
        if ":" in line:
            k, v = line.split(":", 1)
            headers[k.strip()] = v.strip()
    return command, headers, body


def _build_stomp_frame(command: str, headers: dict = None, body: str = "") -> str:
    headers = headers or {}
    lines = [command]
    for k, v in headers.items():
        lines.append(f"{k}:{v}")
    lines.append("")
    if body:
        lines.append(body)
    return "\n".join(lines) + STOMP_NULL


def _sockjs_send(ws, message: str):
    """Send a SockJS-framed message (type 'a' = array)."""
    ws.send("a" + json.dumps([message]))


def _handle_stomp_message(ws, frame: str, subscriptions: dict):
    """Process one STOMP frame and reply via SockJS."""
    command, headers, body = _parse_stomp_frame(frame)

    if command == "CONNECT":
        _sockjs_send(ws, _build_stomp_frame("CONNECTED", {
            "version": "1.2",
            "heart-beat": "0,0",
        }))

    elif command == "SUBSCRIBE":
        sub_id = headers.get("id", "sub-0")
        dest = headers.get("destination", "")
        subscriptions[sub_id] = dest
        receipt = headers.get("receipt")
        if receipt:
            _sockjs_send(ws, _build_stomp_frame("RECEIPT", {"receipt-id": receipt}))

    elif command == "SEND":
        dest = headers.get("destination", "")
        if dest.endswith("/mock") or dest == "/app/mock":
            try:
                req = json.loads(body) if body else {}
                size = int(req.get("size", 1))
                fmt = str(req.get("format", "json"))
                method = str(req.get("method", "GET"))
                resource = str(req.get("resource", "items"))
                payload = generate(size, fmt, method, resource)
            except ValueError as e:
                payload = json.dumps({
                    "error": str(e),
                    "validSizes": VALID_SIZES_KB,
                })
            except Exception as e:
                payload = json.dumps({"error": f"Internal error: {e}"})

            for sub_id, sub_dest in subscriptions.items():
                if "/topic/response" in sub_dest or sub_dest.endswith("/response"):
                    _sockjs_send(ws, _build_stomp_frame("MESSAGE", {
                        "subscription": sub_id,
                        "message-id": f"msg-{random.randint(1, 999999)}",
                        "destination": sub_dest,
                    }, body=payload))

    elif command == "DISCONNECT":
        pass


@sock.route("/ws/<server_id>/<session_id>/websocket")
def stomp_websocket(ws, server_id, session_id):
    """SockJS WebSocket transport + STOMP protocol."""
    subscriptions = {}
    ws.send("o")  # SockJS open frame

    try:
        while True:
            raw = ws.receive()
            if raw is None:
                break

            # SockJS client sends JSON array of messages
            try:
                messages = json.loads(raw)
            except json.JSONDecodeError:
                continue

            if not isinstance(messages, list):
                messages = [messages]

            for msg in messages:
                if isinstance(msg, str):
                    _handle_stomp_message(ws, msg, subscriptions)

    except Exception as e:
        logger.debug("[STOMP] session %s/%s closed: %s", server_id, session_id, e)
