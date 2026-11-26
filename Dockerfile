# syntax=docker/dockerfile:1
# Backend image (local staging today, the real server later). Configuration only from environment variables
# (see deploy/.env.staging.example and .env.example); nothing secret and no dev seed inside the image.
# Tests are not run here (Testcontainers needs Docker): run ./mvnw verify before building.

# ---------- Build: Maven wrapper + JDK 21 ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# A Windows checkout may have CRLF line endings in the wrapper: normalize before running it
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
# Dependencies first (own layer, cached while pom.xml does not change); the cache mount keeps ~/.m2 between builds
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B -DskipTests package \
    && cp target/recrutamento-api-*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---------- Runtime: JRE 21, non-root ----------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -D -u 10001 -G app -h /app app
WORKDIR /app
# Layers from the least to the most frequently changed: a code change only replaces the last one
COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Flyway + context start take a while on the first run
HEALTHCHECK --interval=10s --timeout=5s --start-period=120s --retries=6 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
