# Project Leyden AOT Cache — Eliminating Cold-Start Errors

## Problem

During load tests at 200 RPS, ml-inference causes X errors in the first 3-4 seconds.
Root cause: JIT compilation of Spring MVC, Jackson, and Tomcat internals makes ml-inference
respond in ~87ms during cold-start, exceeding the 85ms `readTimeout`. After warmup, response
time drops to <1ms and errors stop completely.

Partner-simulator doesn't have this issue because it runs later in the request pipeline,
by which time the HTTP client code paths are already JIT-compiled.

## Solution: Leyden AOT Cache (available in JDK 25+)

Pre-compile hot methods ahead of time using a training run. The JVM loads pre-compiled code
on startup — no interpreter, no C1/C2 warmup. ml-inference responds in <1ms from request #1.

### Available JVM flags (verified on Temurin 25.0.2)

```
AOTMode=off|record|create|auto|on
AOTConfiguration=<path>     # training profile output/input
AOTCacheOutput=<path>       # cache file output (create mode)
AOTCache=<path>             # cache file input (production)
AOTClassLinking=true        # pre-link classes
AOTCompileEagerly=true      # experimental, compile all recorded methods
```

### Three-step workflow

**Step 1 — Training run** (record class loading and method profiles):
```bash
java -XX:AOTMode=record -XX:AOTConfiguration=app.aotconf \
     $JAVA_OPTS -jar app.jar
# Send representative traffic (e.g., load test warmup), then shut down
```

**Step 2 — Create cache** (pre-compile recorded hot methods):
```bash
java -XX:AOTMode=create \
     -XX:AOTConfiguration=app.aotconf \
     -XX:AOTCacheOutput=app.aot \
     -jar app.jar
# This does NOT run the app — it just builds the cache file
```

**Step 3 — Production** (load pre-compiled code):
```bash
java -XX:AOTCache=app.aot $JAVA_OPTS -jar app.jar
```

### Dockerfile example (multi-stage with AOT cache)

```dockerfile
FROM eclipse-temurin:25-jre-alpine AS base

RUN addgroup -g 1000 adserve && \
    adduser -u 1000 -G adserve -D -h /app adserve
WORKDIR /app
COPY build/libs/*.jar app.jar
RUN chown -R adserve:adserve /app
USER adserve

# --- Training stage ---
FROM base AS training
RUN java -XX:AOTMode=record -XX:AOTConfiguration=app.aotconf \
         -jar app.jar &  \
    sleep 15 && \
    wget -qO- http://localhost:8081/actuator/health && \
    wget -qO /dev/null -post-data '{"userId":"warmup","traceId":"aot"}' \
         --header='Content-Type: application/json' \
         http://localhost:8081/api/v1/predict && \
    kill %1 && wait

RUN java -XX:AOTMode=create \
         -XX:AOTConfiguration=app.aotconf \
         -XX:AOTCacheOutput=app.aot \
         -jar app.jar

# --- Production stage ---
FROM base AS production
COPY --from=training /app/app.aot app.aot

EXPOSE 8081
ENV JAVA_OPTS="-Xms272m -Xmx272m \
    -XX:+UseZGC \
    -XX:ReservedCodeCacheSize=48m \
    -XX:MaxMetaspaceSize=48m \
    --enable-preview \
    -Djdk.tracePinnedThreads=short \
    -Xlog:gc*:stdout:time,level,tags \
    -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java -XX:AOTCache=app.aot $JAVA_OPTS -jar app.jar"]
```

## Trade-offs

| Aspect              | Detail                                                        |
|---------------------|---------------------------------------------------------------|
| Cache size          | ~50-150MB added to Docker image                               |
| Build time          | Longer (training run + cache creation during docker build)    |
| Compatibility       | Cache tied to exact JAR + JDK version + CPU architecture      |
| Cache invalidation  | Any code change requires cache rebuild (automate in CI)       |
| Risk                | Zero — invalid/missing cache falls back to normal JIT silently|
| Peak throughput     | Unchanged — JIT still runs on top, cache is just a head start |
| Code changes needed | None                                                          |

## Applicable services

All 6 services would benefit, but priority order based on cold-start impact:

1. **ml-inference** — most affected, first HTTP service in request pipeline
2. **ad-orchestration** — heaviest service, most classes to load
3. **partner-simulator** — handles highest request volume
4. user-service, segment-service, targeting-service — gRPC, less affected

## References

- OpenJDK Project Leyden: https://openjdk.org/projects/leyden/
- JDK 24+ AOT flags: https://openjdk.org/projects/leyden/slides/leyden-heidinga-devnexus-2024-03.pdf
