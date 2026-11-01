# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY ai-gateway/pom.xml ai-gateway/
COPY ai-gateway/src ./ai-gateway/src
COPY book-assistant-lite/pom.xml book-assistant-lite/
RUN mvn package -pl ai-gateway -am -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/ai-gateway/target/ai-gateway-1.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]