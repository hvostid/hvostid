# syntax=docker/dockerfile:1.7

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

# Gradle wrapper and root configuration.
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./

# Per-module build scripts. Only the files required for dependency
# resolution are copied; sources are intentionally excluded so that source
# changes do not invalidate this layer.
COPY common/build.gradle.kts common/
COPY api-gateway/build.gradle.kts api-gateway/
COPY auth-service/build.gradle.kts auth-service/
COPY listing-service/build.gradle.kts listing-service/
COPY passport-service/build.gradle.kts passport-service/
COPY matching-service/build.gradle.kts matching-service/

# Resolve dependencies for every module. The `dependencies` task forces
# resolution of all configurations, which triggers downloads of POMs and
# artifacts into /root/.gradle. No cache mount is used here on purpose:
# we want the warmed cache to persist in the image layer.
RUN chmod +x ./gradlew && \
    ./gradlew --no-daemon \
        :common:dependencies \
        :api-gateway:dependencies \
        :auth-service:dependencies \
        :listing-service:dependencies \
        :passport-service:dependencies \
        :matching-service:dependencies

# --- Build stage ----------------------------------------------------------
# Builds the bootJar for the requested service. Every parallel invocation
# of this stage starts from the same deps image, so each container has its
# own private /root/.gradle copy. No cross-service locking is possible.
FROM deps AS builder

ARG SERVICE_NAME

# Bring in the rest of the repository. The .dockerignore excludes build
# outputs, .gradle, frontend, etc.
COPY . .

# A per-service cache id is used so that incremental rebuilds of a single
# service (e.g. on a developer machine) reuse Gradle's task output cache.
# sharing=locked is a defensive measure: even if the same SERVICE_NAME is
# accidentally built twice in parallel, BuildKit will serialize access.
RUN --mount=type=cache,id=gradle-build-${SERVICE_NAME},target=/root/.gradle,sharing=locked \
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
