FROM eclipse-temurin:25-jre-alpine

ARG SERVICE_NAME
ARG SERVICE_PORT=8080

LABEL maintainer="hvostid-team"
LABEL service="${SERVICE_NAME}"

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY ${SERVICE_NAME}/build/libs/${SERVICE_NAME}-*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE ${SERVICE_PORT}

ENTRYPOINT ["java", "-jar", "app.jar"]
