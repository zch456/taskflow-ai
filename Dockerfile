FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl

RUN addgroup -S taskflow && adduser -S taskflow -G taskflow

WORKDIR /app

COPY target/taskflow-ai-*.jar app.jar

USER taskflow

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -sf http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "app.jar"]
