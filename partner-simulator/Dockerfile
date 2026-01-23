# Runtime image for partner-simulator (REST)
# Build JAR first: ./gradlew :partner-simulator:bootJar
FROM eclipse-temurin:25-jre-alpine

RUN addgroup -g 1000 adserve && \
    adduser -u 1000 -G adserve -D -h /app adserve

WORKDIR /app

COPY build/libs/*.jar app.jar

RUN chown -R adserve:adserve /app

USER adserve

# HTTP port
EXPOSE 8082

ENV JAVA_OPTS="-Xms256m -Xmx256m \
    -XX:+UseZGC \
    --enable-preview \
    -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
