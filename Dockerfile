# syntax=docker/dockerfile:1
#
# securetrans-backend — Spring Boot 3.3.4 / Java 21 / Maven
#
# Build:
#   docker build -t securetrans-backend .
#
# Run (needs a reachable Postgres and, optionally, the fraud-detection engine):
#   docker run -p 8080:8080 \
#     -e DB_USER=securetrans \
#     -e DB_PASSWORD=changeme \
#     -e JWT_SECRET=change-this-secret-in-production-min-32-chars \
#     -e FRAUD_ENGINE_URL=http://fraud-engine:8500 \
#     -e CORS_ALLOWED_ORIGINS=https://app.example.com \
#     securetrans-backend
#
# Note: application.yml expects the datasource host to be reachable as "localhost"
# by default — override with SPRING_DATASOURCE_URL when running in Docker/Compose,
# e.g. -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/securetrans

ARG JAVA_VERSION=21

########################################
# 1) Build stage — compile & package with Maven
########################################
FROM maven:3.9-eclipse-temurin-${JAVA_VERSION} AS build
WORKDIR /build

# Copy only the POM first so Maven can resolve/cache dependencies in their own
# Docker layer — this layer is only invalidated when pom.xml actually changes.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

# Now copy the sources and build the jar.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests package

# Explode the fat jar into its Spring Boot layers (dependencies / loader /
# snapshot-dependencies / application) so the runtime image below can copy
# them as separate Docker layers — app code changes won't bust the
# (much larger, rarely-changing) dependencies layer.
RUN JAR_FILE=$(ls target/*.jar | grep -v ".original") && \
    mkdir -p target/extracted && \
    java -Djarmode=layertools -jar "$JAR_FILE" extract --destination target/extracted

########################################
# 2) Runtime stage — slim JRE, non-root user
########################################
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS runtime
WORKDIR /app

# Run as a dedicated, non-root user.
RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build --chown=spring:spring /build/target/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /build/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /build/target/extracted/application/ ./

USER spring

EXPOSE 8080

# Extra JVM flags (heap sizing, GC, etc.) can be injected at `docker run` time,
# e.g. -e JAVA_OPTS="-Xmx512m -XX:+UseG1GC"
ENV JAVA_OPTS=""

# Requires spring-boot-starter-actuator (not currently a dependency of this
# project) to expose /actuator/health. Add the dependency, then uncomment:
# HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
#   CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]