# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy only pom files first to leverage Docker layer caching for dependencies
COPY coom-transpiler/pom.xml coom-transpiler/pom.xml
COPY coom-transpiler/coom-compiler/pom.xml coom-transpiler/coom-compiler/pom.xml
COPY coom-transpiler/coom-web/pom.xml coom-transpiler/coom-web/pom.xml

# Pre-fetch dependencies (offline) to speed up subsequent builds
RUN mvn -q -f coom-transpiler/pom.xml -DskipTests dependency:go-offline

# Now copy the full sources
COPY coom-transpiler coom-transpiler

# Build the compiler module into the local repo so coom-web can resolve it
RUN mvn -q -f coom-transpiler/pom.xml -DskipTests -pl coom-compiler -am install

# Build the web app (fast-jar layout) with Quarkus
RUN mvn -q -f coom-transpiler/coom-web/pom.xml -DskipTests -Dquarkus.package.type=fast-jar package \
    && test -f coom-transpiler/coom-web/target/classes/org/acme/ViewCoomController.class \
    && ls -la coom-transpiler/coom-web/target \
    && ls -la coom-transpiler/coom-web/target/quarkus-app \
    && test -f coom-transpiler/coom-web/target/quarkus-app/quarkus-run.jar


# ---------- Runtime stage (JVM) ----------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Copy Quarkus fast-jar layout
COPY --from=build /workspace/coom-transpiler/coom-web/target/quarkus-app ./quarkus-app

# Default HTTP port
EXPOSE 8080

# Ensure Quarkus binds on all interfaces inside the container
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0"

# Start the Quarkus application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar quarkus-app/quarkus-run.jar"]
