# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY auth-server/pom.xml .
RUN mvn dependency:go-offline -q
COPY auth-server/src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
LABEL org.opencontainers.image.title="openidentity-auth-server"
LABEL org.opencontainers.image.description="OpenIdentity auth server"

RUN groupadd -r openid && useradd -r -g openid openid
WORKDIR /app
COPY --from=build /build/target/quarkus-app ./

RUN chown -R openid:openid /app
USER openid

EXPOSE 7070
ENV JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom -Dquarkus.http.host=0.0.0.0"

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -sf http://localhost:7070/q/health/ready || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar quarkus-run.jar"]
