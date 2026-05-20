"""
GraphQL over WebSocket (graphql-ws protocol) for Subscription operations.

Connect via:
  ws://localhost:8080/graphql-ws       WS  (plain)
  wss://localhost:8443/graphql-ws        WSS (TLS)

Supports mockStream subscription — equivalent of MockGraphQLController.mockStream().
"""
import json
import logging
import threading
import uuid
from datetime import datetime, timezone

from app.service.payload_generator import generate, VALID_SIZES_KB
from app.websocket_handlers.ws import sock

logger = logging.getLogger(__name__)

MIN_INTERVAL_MS = 500


def _mock_response_dict(method: str, resource: str, size_kb: int, fmt: str) -> dict:
    payload = generate(size_kb, fmt, method, resource)
    return {
        "requestId": str(uuid.uuid4()),
        "method": method.upper(),
        "resource": resource,
        "sizeKb": size_kb,
        "format": fmt.lower(),
        "byteLength": len(payload.encode("utf-8")),
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "payload": payload,
    }


def _parse_mock_stream_vars(variables: dict) -> tuple:
    resource = variables.get("resource", "events")
    size = int(variables.get("size", 1))
    fmt = str(variables.get("format", "json"))
    method = str(variables.get("method", "GET"))
    interval_ms = max(
        MIN_INTERVAL_MS,
        int(variables.get("intervalMs", variables.get("interval_ms", 1000))),
    )
    return resource, size, fmt, method, interval_ms


def init_graphql_ws(app):
    """WebSocket routes are registered via @sock.route decorators."""


@sock.route("/graphql-ws")
def graphql_ws(ws):
    """Handle graphql-ws protocol connections."""
    subscriptions = {}  # sub_id -> (stop_event, thread)

    def stop_all():
        for sub_id, (stop_evt, thread) in list(subscriptions.items()):
            stop_evt.set()
            if thread.is_alive():
                thread.join(timeout=2)
        subscriptions.clear()

    def send_frame(frame: dict):
        ws.send(json.dumps(frame))

    def start_mock_stream(sub_id: str, variables: dict):
        resource, size, fmt, method, interval_ms = _parse_mock_stream_vars(variables)
        stop_evt = threading.Event()

        def emit_loop():
            while not stop_evt.is_set():
                try:
                    data = _mock_response_dict(method, resource, size, fmt)
                    send_frame({
                        "id": sub_id,
                        "type": "next",
                        "payload": {"data": {"mockStream": data}},
                    })
                except ValueError as e:
                    send_frame({
                        "id": sub_id,
                        "type": "error",
                        "payload": [{"message": str(e), "validSizes": VALID_SIZES_KB}],
                    })
                    break
                except Exception as e:
                    send_frame({
                        "id": sub_id,
                        "type": "error",
                        "payload": [{"message": str(e)}],
                    })
                    break
                stop_evt.wait(interval_ms / 1000.0)

        thread = threading.Thread(target=emit_loop, daemon=True)
        subscriptions[sub_id] = (stop_evt, thread)
        thread.start()

    try:
        while True:
            raw = ws.receive()
            if raw is None:
                break

            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                send_frame({"type": "error", "payload": [{"message": "Invalid JSON frame"}]})
                continue

            msg_type = msg.get("type")
            sub_id = str(msg.get("id", ""))

            if msg_type == "connection_init":
                send_frame({"type": "connection_ack"})

            elif msg_type == "subscribe":
                payload = msg.get("payload") or {}
                variables = payload.get("variables") or {}
                query = (payload.get("query") or "").lower()
                if "mockstream" not in query.replace("_", ""):
                    send_frame({
                        "id": sub_id,
                        "type": "error",
                        "payload": [{"message": "Only mockStream subscription is supported"}],
                    })
                    continue
                if sub_id in subscriptions:
                    subscriptions[sub_id][0].set()
                start_mock_stream(sub_id, variables)

            elif msg_type == "complete":
                if sub_id in subscriptions:
                    subscriptions[sub_id][0].set()
                    del subscriptions[sub_id]
                send_frame({"id": sub_id, "type": "complete"})

            elif msg_type == "ping":
                send_frame({"type": "pong"})

    finally:
        stop_all()
