# syntax=docker/dockerfile:1
ARG JDK_BUILD_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:17-jdk-alpine
ARG JRE_RUNTIME_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:17-jre-alpine

FROM ${JDK_BUILD_IMAGE} AS build

WORKDIR /workspace

COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/mvnw
COPY backend/pom.xml backend/pom.xml

# 限制 Maven/JVM 构建期堆，避免在 2G 小内存服务器上构建时把内存吃光导致整机卡死(OOM)。
ENV MAVEN_OPTS="-Xmx512m"
RUN --mount=type=cache,target=/root/.m2 chmod +x backend/mvnw && cd backend && ./mvnw -B -DskipTests dependency:go-offline

COPY backend/src backend/src
RUN --mount=type=cache,target=/root/.m2 cd backend && ./mvnw -B -DskipTests package

FROM ${JRE_RUNTIME_IMAGE}

WORKDIR /app

ENV TZ=Asia/Shanghai \
    SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

RUN addgroup -S -g 10001 blog && adduser -S -D -u 10001 -G blog blog \
    && mkdir -p /app/uploads /app/backups && chown -R blog:blog /app

COPY --chown=blog:blog --from=build /workspace/backend/target/blog-backend-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001

EXPOSE 8080

# 限制运行期 JVM 堆：默认 JVM 会按物理内存 1/4 起步并可能涨到很大，小内存机上会和 postgres/frontend 抢内存。
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=4 CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
