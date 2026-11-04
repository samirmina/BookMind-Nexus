# syntax=docker/dockerfile:1

# ---------------------------------------------------------------
# Stage 1: Build the application with Maven + JDK 21
# ---------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

# Cache dependencies separately for faster rebuilds
COPY pom.xml ./
COPY book-mind-nexus/pom.xml ./book-mind-nexus/pom.xml
COPY ai-gateway/pom.xml ./ai-gateway/pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl ai-gateway dependency:go-offline

# Copy sources and package
COPY . ./

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl ai-gateway -am package -DskipTests

# ---------------------------------------------------------------
# Stage 2: Run the application with JRE 21
# ---------------------------------------------------------------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create a non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the built fat JAR from the builder stage
COPY --from=builder /workspace/ai-gateway/target/*.jar app.jar

# Create runtime directories
RUN mkdir -p /app/logs /app/cache \
    && chown -R appuser:appuser /app

USER appuser

EXPOSE 8081

# JVM tuning for container memory limits
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.net.preferIPv4Stack=true"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
