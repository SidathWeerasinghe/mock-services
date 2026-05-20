@echo off
echo ============================================
echo  Generating self-signed keystore for WSS
echo ============================================
keytool -genkeypair ^
  -alias mock-server ^
  -keyalg RSA ^
  -keysize 2048 ^
  -storetype PKCS12 ^
  -keystore src\main\resources\keystore.p12 ^
  -validity 3650 ^
  -storepass changeit ^
  -keypass  changeit ^
  -dname "CN=localhost, OU=Dev, O=MockServer, L=City, ST=State, C=US" ^
  -noprompt
echo.
echo Done! keystore.p12 created in src\main\resources\
pause
