# Runtime image for ad-orchestration
# Build JAR first: ./gradlew :ad-orchestration:bootJar
FROM eclipse-temurin:25-jre-alpine

# Create non-root user
RUN addgroup -g 1000 adserve && \
    adduser -u 1000 -G adserve -D -h /app adserve

WORKDIR /app

# Copy pre-built JAR
COPY build/libs/*.jar app.jar

# Change ownership
RUN chown -R adserve:adserve /app

# Switch to non-root user
USER adserve

# Expose port
EXPOSE 8080

# JVM settings with preview features enabled
ENV JAVA_OPTS="-Xms384m -Xmx384m \
    -XX:+UseZGC \
    -XX:ReservedCodeCacheSize=64m \
    -XX:MaxMetaspaceSize=128m \
    --enable-preview \
    -Djdk.tracePinnedThreads=short \
    -Xlog:gc*:stdout:time,level,tags \
    -Djava.security.egd=file:/dev/./urandom"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
