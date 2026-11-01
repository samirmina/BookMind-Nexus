# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY book-assistant-lite/pom.xml book-assistant-lite/
COPY book-assistant-lite/src ./book-assistant-lite/src
RUN mvn package -pl book-assistant-lite -am -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/book-assistant-lite/target/BookMind-Nexus-1.0.0.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]