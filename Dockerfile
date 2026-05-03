# syntax=docker/dockerfile:1.7-labs

# --- Dependencies stage ---------------------------------------------------
# Pre-warms the Gradle dependency cache once and bakes it into a Docker
# image layer. Each per-service builder stage below does FROM deps, so all
# parallel builds inherit the warmed cache via copy-on-write without ever
# sharing a mutable directory. That eliminates the journal-1.lock contention
# that occurs when multiple Gradle processes mount the same BuildKit cache.
#
# This stage depends only on Gradle build scripts, the wrapper, and the
# version catalog. It is reused from the BuildKit layer cache as long as
# none of those files change. Source files have no effect on it.
FROM eclipse-temurin:25-jdk-alpine AS deps

WORKDIR /workspace

# Gradle wrapper and root configuration. --chmod=755 guarantees gradlew is
# executable regardless of the host's filesystem (e.g. Windows contexts
# that do not preserve the Unix exec bit).
COPY --chmod=755 gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./

# Per-module build scripts. --parents preserves the directory layout so
# every <module>/build.gradle.kts lands under its own directory. Sources
# are deliberately excluded so source changes do not invalidate this layer.
COPY --parents */build.gradle.kts ./

# Resolve dependencies for every subproject discovered on disk. The
# `dependencies` task forces resolution of all configurations, which
# triggers downloads of POMs and artifacts into /root/.gradle. No cache
# mount is used here on purpose: we want the warmed cache to persist in
# the image layer.
RUN set -eu; \
    TASKS=""; \
    for d in */build.gradle.kts; do TASKS="$TASKS :$(dirname "$d"):dependencies"; done; \
    ./gradlew --no-daemon $TASKS

# --- Build stage ----------------------------------------------------------
# Builds the bootJar for the requested service. Every parallel invocation
# of this stage starts from the same deps image, so each container has its
# own private /root/.gradle copy. No cross-service locking is possible.
FROM deps AS builder

ARG SERVICE_NAME

# Bring in the rest of the repository. The .dockerignore excludes build
# outputs, .gradle, frontend, etc.
COPY . .

# Reapply the exec bit on gradlew: COPY . . above overwrites it with the
# host copy, which may be missing the Unix exec bit on Windows contexts.
COPY --chmod=755 gradlew gradlew

# No cache mount on /root/.gradle: the deps stage already baked the
# resolved dependencies into this layer, and a BuildKit cache mount would
# shadow them with an empty volume on every build.
RUN ./gradlew :${SERVICE_NAME}:bootJar --no-daemon -x test && \
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
