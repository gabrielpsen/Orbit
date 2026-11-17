# syntax=docker/dockerfile:1.7
FROM node:22-alpine AS ui
WORKDIR /ui
COPY ui/package.json ui/package-lock.json* ./
RUN npm ci
COPY ui ./
RUN npm run build

FROM maven:3.10.0-eclipse-temurin-21-noble AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
COPY --from=ui /src/main/resources/static ./src/main/resources/static
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
RUN groupadd --system --gid 10001 orbit && useradd --system --uid 10001 --gid orbit --home-dir /app orbit
COPY --from=build --chown=orbit:orbit /workspace/target/orbit-0.1.0-SNAPSHOT.jar /app/orbit.jar
USER 10001:10001
EXPOSE 8080 9090
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"
ENTRYPOINT ["java", "-jar", "/app/orbit.jar"]
