# syntax=docker/dockerfile:1.7

# --- Build stage ----------------------------------------------------------
# Builds the bootJar for the requested module from sources. Uses BuildKit
# cache mounts so the Gradle dependency cache survives across builds.
FROM eclipse-temurin:25-jdk-alpine AS builder

ARG SERVICE_NAME

WORKDIR /workspace

# Copy the entire repo (build/.gradle/.git/etc are excluded via .dockerignore).
COPY . .

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x ./gradlew && \
    ./gradlew :${SERVICE_NAME}:bootJar --no-daemon -x test

# --- Runtime stage --------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine

ARG SERVICE_NAME
ARG SERVICE_PORT=8080

LABEL maintainer="hvostid-team"
LABEL service="${SERVICE_NAME}"

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /workspace/${SERVICE_NAME}/build/libs/${SERVICE_NAME}-*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE ${SERVICE_PORT}

ENTRYPOINT ["java", "-jar", "app.jar"]
