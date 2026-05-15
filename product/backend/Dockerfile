FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY . .
ARG APP_MODULE
RUN gradle --no-daemon :apps:${APP_MODULE}:bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
ARG APP_MODULE
COPY --from=build /workspace/apps/${APP_MODULE}/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
