@echo off
echo ============================================
echo  Generating self-signed certificate for TLS
echo ============================================
openssl req ^
  -x509 ^
  -newkey rsa:2048 ^
  -keyout certs\server.key ^
  -out certs\server.crt ^
  -days 3650 ^
  -nodes ^
  -subj "/CN=localhost/OU=Dev/O=MockServer/L=City/ST=State/C=US"
echo.
echo Done! server.crt and server.key created in certs\
pause
