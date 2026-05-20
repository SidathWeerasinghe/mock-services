FROM node:20-alpine
WORKDIR /app

RUN apk add --no-cache openssl

COPY package.json package-lock.json* ./
RUN npm install --production

COPY src ./src/
COPY static ./static/

EXPOSE 8080 8442 8443 8444

ENV MOCK_SSL_ENABLED=true
ENV MOCK_HTTP_PORT=8080
ENV MOCK_TLS12_PORT=8442
ENV MOCK_TLS13_PORT=8443
ENV MOCK_TLS12_13_PORT=8444
ENV MOCK_STATIC_DIR=static
ENV MOCK_CERT_DIR=certs
ENV NODE_ENV=production

CMD ["node", "src/index.js"]
