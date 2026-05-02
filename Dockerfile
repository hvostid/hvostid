# syntax=docker/dockerfile:1.7

# --- Build stage ----------------------------------------------------------
# Builds the bootJar for the requested module from sources. Uses BuildKit
# cache mounts so the Gradle dependency cache survives across builds.
#
# ARG SERVICE_NAME is declared AFTER the COPY so the COPY layer is
# service-independent and shared across all backend services.
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /workspace

# Copy the entire repo (build/.gradle/.git/etc are excluded via .dockerignore).
COPY . .

ARG SERVICE_NAME

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x ./gradlew && \
    ./gradlew :${SERVICE_NAME}:bootJar --no-daemon -x test && \
    find ${SERVICE_NAME}/build/libs/ -name "${SERVICE_NAME}-*.jar" ! -name "*-plain.jar" -exec cp {} /workspace/app.jar \;

# --- Runtime stage --------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine

ARG SERVICE_NAME
ARG SERVICE_PORT=8080

LABEL maintainer="hvostid-team"
LABEL service="${SERVICE_NAME}"

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /workspace/app.jar app.jar

USER appuser

EXPOSE ${SERVICE_PORT}

ENTRYPOINT ["java", "-jar", "app.jar"]
