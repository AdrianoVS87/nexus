# --- Build stage ---
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
RUN apk add --no-cache maven

COPY pom.xml .
COPY nexus-common/pom.xml nexus-common/
COPY order-service/pom.xml order-service/
COPY payment-service/pom.xml payment-service/
COPY inventory-service/pom.xml inventory-service/
COPY notification-service/pom.xml notification-service/
COPY api-gateway/pom.xml api-gateway/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl inventory-service -am -B -q || true

COPY nexus-common/src nexus-common/src
COPY inventory-service/src inventory-service/src

RUN mvn package -pl inventory-service -am -DskipTests -B -q

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S nexus && adduser -S nexus -G nexus
WORKDIR /app
COPY --from=build /workspace/inventory-service/target/*.jar app.jar
USER nexus
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
