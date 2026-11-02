# API. 빌드와 실행을 나눠 실행 이미지는 JRE만 갖는다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
LABEL org.opencontainers.image.source=https://github.com/sjh9714/cuk-alimi
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -Duser.timezone=Asia/Seoul"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
