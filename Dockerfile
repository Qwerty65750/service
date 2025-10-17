FROM gradle:8.8-jdk17-alpine AS build
WORKDIR /home/gradle/project

COPY build.gradle settings.gradle ./
COPY gradle gradle
COPY src src

RUN gradle --no-daemon clean bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /home/gradle/project/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]


