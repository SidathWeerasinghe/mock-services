# Build
FROM golang:1.21-alpine AS build
WORKDIR /app
RUN apk add --no-cache git
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o /mockserver ./cmd/mockserver

# Run
FROM alpine:3.19
WORKDIR /app
RUN apk add --no-cache ca-certificates
COPY --from=build /mockserver .
COPY static ./static/
EXPOSE 8080 8442 8443 8444
ENV MOCK_SSL_ENABLED=true
ENV MOCK_HTTP_PORT=8080
ENV MOCK_TLS12_PORT=8442
ENV MOCK_TLS13_PORT=8443
ENV MOCK_TLS12_13_PORT=8444
ENV MOCK_STATIC_DIR=static
ENV MOCK_CERT_DIR=certs
CMD ["./mockserver"]
