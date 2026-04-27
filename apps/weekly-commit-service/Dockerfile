# Multi-stage Dockerfile for the weekly-commit-service backend.
#
# Stage 1: build the Spring Boot fat jar via the Maven wrapper.
# Stage 2: extract the layered jar and run it on a JRE-only runtime image.
#
# The layered-jar pattern (`spring-boot-jarmode-layertools`) splits dependencies,
# spring-boot-loader, snapshot deps, and application classes into separate Docker
# layers so iterative `docker build` runs reuse the dep layer (~85% of the jar
# weight) when only application code changes. Cuts demo-deploy rebuild time from
# ~70s to ~12s on a warm cache.

# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Maven wrapper lives in the service module. Copy it + the descriptors first so
# `dependency:go-offline` can resolve the dep tree before the rest of the source
# changes. Source-only edits then hit the warm `~/.m2` cache.
COPY apps/weekly-commit-service/.mvn .mvn
COPY apps/weekly-commit-service/mvnw .
COPY apps/weekly-commit-service/pom.xml .
RUN chmod +x ./mvnw && ./mvnw -B -q dependency:go-offline -DskipTests

# Now copy the rest of the source. Exclude the `target/` dir via .dockerignore
# so a previous local build doesn't shadow this image's compile output.
COPY apps/weekly-commit-service/src ./src

# Skip tests in the image build -- CI's backend-pr.yml workflow runs them. The
# image build just produces a jar.
RUN ./mvnw -B -q package -DskipTests

# Extract the layered jar so each layer becomes its own Docker layer in stage 2.
RUN java -Djarmode=layertools -jar target/weekly-commit-service.jar extract --destination /workspace/extracted

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for the app process. Spring Boot doesn't need any of the things
# root provides; running as non-root is a baseline container hardening step.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app

# Copy the extracted layers in dependency order so an application-only change
# only invalidates the last (smallest) layer on rebuilds.
COPY --from=build --chown=spring:spring /workspace/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/application/ ./

# Defaults aimed at small RDS instances and Fargate. The G1GC max-pause + container
# memory awareness flags keep the heap honest under the 1 GB Fargate task we use
# for the demo. Override at runtime via `JAVA_OPTS` env on the task definition.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:MaxGCPauseMillis=200" \
    SPRING_PROFILES_ACTIVE="e2e,demo" \
    SERVER_PORT=8080

EXPOSE 8080

# `org.springframework.boot.loader.launch.JarLauncher` (Spring Boot 3.2+) is the
# entry point of the layered jar; it lives in the spring-boot-loader layer.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
