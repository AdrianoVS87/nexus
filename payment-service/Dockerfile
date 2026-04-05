# --- Build stage ---
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
RUN apk add --no-cache maven

COPY pom.xml .
COPY nexus-common/pom.xml nexus-common/
COPY payment-service/pom.xml payment-service/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl payment-service -am -B -q || true

COPY nexus-common/src nexus-common/src
COPY payment-service/src payment-service/src

RUN mvn package -pl payment-service -am -DskipTests -B -q

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S nexus && adduser -S nexus -G nexus
WORKDIR /app
COPY --from=build /workspace/payment-service/target/*.jar app.jar
USER nexus
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
