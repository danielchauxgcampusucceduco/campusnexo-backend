# ---- Build stage -------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Dependencies are resolved in their own layer so code changes do not download them again.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
# Tests run in CI (they need Docker for Testcontainers), not inside the image build.
RUN mvn -B -q -DskipTests package

# ---- Runtime stage -----------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/campusnexo-backend.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
