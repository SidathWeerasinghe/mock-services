# Mock API Server — Go

A Go implementation of the Mock API Server with the **same logic and endpoints** as the Java (Spring Boot) and Python (Flask) versions.

## Features

| Feature | Endpoint |
|---------|----------|
| REST API | `/api/{resource}?size={kb}&format={json\|xml\|text\|html}` |
| STOMP/SockJS | `http://localhost:8080/ws` |
| Raw WebSocket | `ws://localhost:8080/raw-ws` |
| GraphQL | `POST /graphql` |
| GraphiQL | `GET /graphiql` |
| GraphQL WS | `ws://localhost:8080/graphql-ws` |
| Health | `GET /health` |
| Info | `GET /info` |
| TLS Info | `GET /tls-info` |
| Test UI | `GET /` |

## Payload sizes (KB)

`1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20`

## Quick start

```bash
cd go
go mod tidy
go run ./cmd/mockserver
```

Server listens on:

- `http://localhost:8080` (HTTP)
- `https://localhost:8442` (TLS 1.2)
- `https://localhost:8443` (TLS 1.3)
- `https://localhost:8444` (TLS 1.2 + 1.3)

HTTP only:

```bash
set MOCK_SSL_ENABLED=false
go run ./cmd/mockserver
```

## Docker

```bash
cd go
docker build -t mock-server-go .
docker run -p 10080:8080 -p 18442:8442 -p 18443:8443 -p 18444:8444 mock-server-go
```

## Project layout

```
go/
├── cmd/mockserver/main.go
├── internal/
│   ├── payload/generator.go
│   ├── handler/          # REST, info, GraphQL HTTP, TLS
│   ├── graphql/          # GraphQL executor
│   ├── ws/               # raw-ws, graphql-ws, STOMP/SockJS
│   ├── cert/             # self-signed TLS certs
│   └── server/           # router + multi-port runner
└── static/index.html
```
