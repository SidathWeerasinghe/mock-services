# Stage 1: Build dependencies
FROM python:3.11-slim AS build
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libffi-dev \
    libxml2-dev \
    libxslt1-dev \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

# Stage 2: Run the application
FROM python:3.11-slim
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    openssl \
    libxml2 \
    libxslt1.1 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /install /usr/local
COPY app/ ./app/
COPY certs/ ./certs/
COPY requirements.txt .

# 8080: plain HTTP / WS
# 8442: HTTPS TLS 1.2
# 8443: HTTPS TLS 1.3
# 8444: HTTPS TLS 1.2 + 1.3
EXPOSE 8080 8442 8443 8444

ENV MOCK_SSL_ENABLED=true
ENV MOCK_HTTP_PORT=8080
ENV MOCK_TLS12_PORT=8442
ENV MOCK_TLS13_PORT=8443
ENV MOCK_TLS12_13_PORT=8444
ENV PYTHONUNBUFFERED=1

CMD ["python", "-m", "app"]
