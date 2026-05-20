# Mock API Server — Node.js

A Node.js (Express) implementation of the Mock API Server with the **same logic and endpoints** as the Java, Python, and Go versions.

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
cd nodejs
npm install
npm start
```

HTTP only:

```bash
set MOCK_SSL_ENABLED=false
npm start
```

## Docker

```bash
cd nodejs
docker build -t mock-server-node .
docker run -p 11080:8080 -p 19442:8442 -p 19443:8443 -p 19444:8444 mock-server-node
```

Or with docker-compose from the repo root:

```bash
docker-compose up --build mock-server-node
```

## Port map (all implementations)

| Service | HTTP | TLS 1.2 | TLS 1.3 | TLS 1.2+1.3 |
|---------|------|---------|---------|-------------|
| Java | 8080 | 8442 | 8443 | 8444 |
| Python | 9080 | 9442 | 9443 | 9444 |
| Go | 10080 | 18442 | 18443 | 18444 |
| **Node.js** | **11080** | **19442** | **19443** | **19444** |

## Project layout

```
nodejs/
├── src/
│   ├── index.js           # Multi-port HTTP/TLS server
│   ├── app.js             # Express routes
│   ├── payload/generator.js
│   ├── handlers/          # REST, info, GraphQL, TLS
│   ├── graphql/executor.js
│   ├── ws/                # raw-ws, graphql-ws, STOMP
│   └── cert/cert.js
├── static/index.html
├── package.json
└── Dockerfile
```
