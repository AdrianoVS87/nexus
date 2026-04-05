# --- Build stage ---
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
RUN apk add --no-cache maven

COPY pom.xml .
COPY nexus-common/pom.xml nexus-common/
COPY notification-service/pom.xml notification-service/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl notification-service -am -B -q || true

COPY nexus-common/src nexus-common/src
COPY notification-service/src notification-service/src

RUN mvn package -pl notification-service -am -DskipTests -B -q

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S nexus && adduser -S nexus -G nexus
WORKDIR /app
COPY --from=build /workspace/notification-service/target/*.jar app.jar
USER nexus
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
