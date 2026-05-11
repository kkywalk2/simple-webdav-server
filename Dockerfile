FROM gradle:8-jdk17-alpine AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
RUN ./gradlew dependencies --no-daemon
COPY src src
RUN ./gradlew installDist --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/install/simple-webdav ./
RUN mkdir -p /data
VOLUME ["/data"]
ENV WEBDAV_ROOT=/data/webdav
ENV WEBDAV_DB_PATH=/data/webdav.db
ENV WEBDAV_PORT=8080
ENV WEBDAV_HOST=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["bin/simple-webdav"]
