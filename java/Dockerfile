# Stage 1: Build the application
FROM maven:3.8.7-eclipse-temurin-11 AS build
WORKDIR /app

# Copy the pom.xml and src code
COPY pom.xml .
COPY src ./src

# Build the jar
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:11-jre
WORKDIR /app

# Copy the jar from the build stage
COPY --from=build /app/target/mock-api-server-1.0.0.jar app.jar

# Expose the necessary ports
# 8080: Plain HTTP / WS
# 8442: HTTPS / WSS, TLS 1.2 only
# 8443: HTTPS / WSS, TLS 1.3 only (primary connector)
# 8444: HTTPS / WSS, TLS 1.2 + 1.3 (combined)
EXPOSE 8080 8442 8443 8444

# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
