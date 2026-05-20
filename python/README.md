# Mock API Server — Python

A Python (Flask) implementation of the Mock API Server, providing the **same logic and endpoints** as the Java (Spring Boot) version.

## Features

| Feature | Endpoint | Description |
|---------|----------|-------------|
| REST API | `/api/{resource}?size={kb}&format={json\|xml\|text\|html}` | GET, POST, PUT, DELETE |
| Raw WebSocket | `ws://localhost:8080/raw-ws` | JSON frame → payload response |
| GraphQL | `POST /graphql` | Query & Mutation |
| GraphiQL IDE | `GET /graphiql` | Browser-based playground |
| Health | `GET /health` | Liveness probe |
| Info | `GET /info` | Server capabilities |
| TLS Info | `GET /tls-info` | TLS session details |
| Test UI | `GET /` | Interactive test console |

## Payload Sizes

Supported sizes (KB): `1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20`

## Formats

`json` | `xml` | `text` | `html`

## Quick Start

### Local Development

```bash
cd python
pip install -r requirements.txt
python -m app.main
```

Server starts at `http://localhost:8080`

### Docker

```bash
# Build and run Python service only
cd python
docker build -t mock-server-python .
docker run -p 9080:8080 mock-server-python

# Or run both Java + Python with docker-compose
cd ..
docker-compose up --build
```

- **Java** → `http://localhost:8080`
- **Python** → `http://localhost:9080`

## API Examples

```bash
# REST
curl http://localhost:9080/api/orders?size=5&format=json
curl http://localhost:9080/api/users?size=10&format=xml
curl -X POST http://localhost:9080/api/users?size=2&format=json
curl -X PUT http://localhost:9080/api/users/42?size=3&format=xml
curl -X DELETE http://localhost:9080/api/products/99?size=1&format=json

# GraphQL
curl -X POST http://localhost:9080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ mock(resource: \"orders\", size: 5) { requestId sizeKb byteLength payload } }"}'

# Health
curl http://localhost:9080/health

# WebSocket (using wscat)
wscat -c ws://localhost:9080/raw-ws
> {"size":5,"format":"json","method":"GET","resource":"orders"}
```

## Project Structure

```
python/
├── app/
│   ├── __init__.py
│   ├── main.py                          # Flask app factory + entry point
│   ├── error_handlers.py                # Global exception handlers
│   ├── cert_utils.py                    # Self-signed cert generator
│   ├── controller/
│   │   ├── rest_controller.py           # REST API (GET/POST/PUT/DELETE)
│   │   ├── info_controller.py           # /health + /info
│   │   └── tls_info_controller.py       # /tls-info
│   ├── service/
│   │   └── payload_generator.py         # Core payload generation logic
│   ├── graphql_schema/
│   │   └── schema.py                    # GraphQL Query + Mutation
│   ├── websocket_handlers/
│   │   └── raw_ws_handler.py            # Raw WebSocket handler
│   └── static/
│       └── index.html                   # Test console UI
├── certs/                               # TLS certificates (auto-generated)
├── Dockerfile
├── .dockerignore
├── generate-certs.bat
├── requirements.txt
└── README.md
```

## Java vs Python Mapping

| Java (Spring Boot) | Python (Flask) |
|---------------------|----------------|
| `MockApiServerApplication.java` | `app/main.py` |
| `PayloadGeneratorService.java` | `app/service/payload_generator.py` |
| `MockRestController.java` | `app/controller/rest_controller.py` |
| `InfoController.java` | `app/controller/info_controller.py` |
| `TlsInfoController.java` | `app/controller/tls_info_controller.py` |
| `RawWebSocketHandler.java` | `app/websocket_handlers/raw_ws_handler.py` |
| `WebSocketController.java` (STOMP) | `app/websocket_handlers/stomp_sockjs_handler.py` |
| `TomcatConfig.java` | `app/server_runner.py` |
| GraphQL Subscriptions | `app/websocket_handlers/graphql_ws_handler.py` |
| `MockGraphQLController.java` + `schema.graphqls` | `app/graphql_schema/schema.py` |
| `GlobalExceptionHandler.java` | `app/error_handlers.py` |
| `KeystoreInitializer.java` | `app/cert_utils.py` |
| `SecurityConfig.java` | Flask-CORS (open all) |
| `TomcatConfig.java` | Single-port (gunicorn) |
