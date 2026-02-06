# Runtime image for user-service (gRPC)
# Build JAR first: ./gradlew :user-service:bootJar
FROM eclipse-temurin:25-jre-alpine

RUN addgroup -g 1000 adserve && \
    adduser -u 1000 -G adserve -D -h /app adserve

WORKDIR /app

COPY build/libs/*.jar app.jar

RUN chown -R adserve:adserve /app

USER adserve

# gRPC port
EXPOSE 9090

ENV JAVA_OPTS="-Xms256m -Xmx256m \
    -XX:+UseZGC \
    -XX:ReservedCodeCacheSize=48m \
    -XX:MaxMetaspaceSize=64m \
    --enable-preview \
    -Djdk.tracePinnedThreads=short \
    -Xlog:gc*:stdout:time,level,tags \
    -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=3s --start-period=35s --retries=3 \
    CMD wget -qO- http://localhost:9001/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
