# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY book-mind-nexus/pom.xml book-mind-nexus/
COPY book-mind-nexus/src ./book-mind-nexus/src
RUN mvn package -pl book-mind-nexus -am -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/book-mind-nexus/target/book-mind-nexus-1.0.0.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]