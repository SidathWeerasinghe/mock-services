"""
Entry point for the Mock API Server (Python).

Exposes:
  - REST  (HTTP)       : http://localhost:8080/api/{resource}?size={kb}&format={json|xml|text|html}
  - REST  (HTTPS)      : https://localhost:8443/api/{resource}
  - WS                 : ws://localhost:8080/ws         (STOMP/SockJS)
  - WSS                : wss://localhost:8443/ws        (STOMP/SockJS)
  - Raw WS             : ws://localhost:8080/raw-ws
  - Raw WSS            : wss://localhost:8443/raw-ws
  - GraphQL HTTP       : http://localhost:8080/graphql  (Query + Mutation)
  - GraphiQL IDE       : http://localhost:8080/graphiql
  - GraphQL WS         : ws://localhost:8080/graphql-ws  (Subscription)
  - GraphQL WSS        : wss://localhost:8443/graphql-ws (Subscription)

TLS version test endpoints (/tls-info):
  - https://localhost:8442/tls-info  → TLS 1.2 only
  - https://localhost:8443/tls-info  → TLS 1.3 only  (primary)
  - https://localhost:8444/tls-info  → TLS 1.2 + 1.3 (combined)

All payload endpoints accept:
  size   : 1,2,3,4,5,6,7,8,9,10,15,20 (KB)
  format : json | xml | text | html
"""
import json
import os
import logging

from flask import Flask, request as flask_request, jsonify, Response
from flask_cors import CORS

from app.controller.rest_controller import rest_bp
from app.controller.info_controller import info_bp
from app.controller.tls_info_controller import tls_bp
from app.websocket_handlers.raw_ws_handler import init_websocket
from app.websocket_handlers.graphql_ws_handler import init_graphql_ws
from app.websocket_handlers.stomp_sockjs_handler import init_stomp
from app.graphql_schema.schema import schema
from app.error_handlers import register_error_handlers

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
)
logger = logging.getLogger(__name__)

# ── GraphiQL HTML template ────────────────────────────────────────────────────
GRAPHIQL_HTML = """<!DOCTYPE html>
<html>
<head>
  <title>GraphiQL — Mock API Server</title>
  <link href="https://unpkg.com/graphiql@3/graphiql.min.css" rel="stylesheet" />
</head>
<body style="margin:0;overflow:hidden;">
  <div id="graphiql" style="height:100vh;"></div>
  <script crossorigin src="https://unpkg.com/react@18/umd/react.production.min.js"></script>
  <script crossorigin src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script>
  <script crossorigin src="https://unpkg.com/graphiql@3/graphiql.min.js"></script>
  <script>
    const fetcher = GraphiQL.createFetcher({ url: '/graphql' });
    ReactDOM.render(
      React.createElement(GraphiQL, { fetcher }),
      document.getElementById('graphiql'),
    );
  </script>
</body>
</html>"""


def create_app() -> Flask:
    """Application factory — creates and configures the Flask app."""
    static_dir = os.path.join(os.path.dirname(__file__), "static")
    app = Flask(__name__, static_folder=static_dir, static_url_path="")

    # ── CORS (open everything for demo purposes) ──────────────────────────────
    CORS(app, resources={r"/*": {"origins": "*"}})

    # ── Register Blueprints ───────────────────────────────────────────────────
    app.register_blueprint(rest_bp)
    app.register_blueprint(info_bp)
    app.register_blueprint(tls_bp)

    # ── GraphQL endpoint (manual, no flask-graphql needed) ────────────────────
    @app.route("/graphql", methods=["GET", "POST"])
    def graphql_endpoint():
        if flask_request.method == "GET":
            query = flask_request.args.get("query", "")
            variables = flask_request.args.get("variables", "{}")
        else:
            content_type = flask_request.content_type or ""
            if "application/json" in content_type:
                body = flask_request.get_json(silent=True) or {}
            else:
                body = {}
            query = body.get("query", "")
            variables = body.get("variables", {})

        if isinstance(variables, str):
            try:
                variables = json.loads(variables)
            except (json.JSONDecodeError, TypeError):
                variables = {}

        result = schema.execute(query, variables=variables)
        response_data = {}
        if result.data:
            response_data["data"] = result.data
        if result.errors:
            response_data["errors"] = [
                {"message": str(e)} for e in result.errors
            ]
        return jsonify(response_data)

    @app.route("/graphiql", methods=["GET"])
    def graphiql_ide():
        return Response(GRAPHIQL_HTML, mimetype="text/html")

    # ── WebSocket handlers (single shared Sock instance) ───────────────────────
    from app.websocket_handlers.ws import sock as _ws_sock
    _ws_sock.init_app(app)
    init_websocket(app)
    init_graphql_ws(app)
    init_stomp(app)

    # ── Error handlers ────────────────────────────────────────────────────────
    register_error_handlers(app)

    # ── Static files (serve index.html at root) ──────────────────────────────
    @app.route("/")
    def index():
        return app.send_static_file("index.html")

    logger.info("Mock API Server (Python) initialized")
    return app


# ── Run directly ──────────────────────────────────────────────────────────────

if __name__ == "__main__":
    from app.server_runner import run_servers

    app = create_app()
    run_servers(app)
