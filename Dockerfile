# ─────────────────────────────────────────────────────────────────────────────
# Payment Gateway — Multi-stage Dockerfile
#
# Stage 1: Build the application using Maven
# Stage 2: Run with a minimal JRE image
#
# Usage:
#   docker build -t payment-gateway .
#   docker-compose --profile app up -d
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build ──
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw package -DskipTests -B

# ── Stage 2: Run ──
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/PaymentGateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

