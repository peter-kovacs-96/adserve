# HTTP Client Architecture (Spring Boot 4.x Best Practices)

## Overview

This document describes the enterprise-grade HTTP client configuration using modern Spring Boot 4.x patterns:

- **Type-safe configuration** via `@ConfigurationProperties` records
- **Property-based timeouts** using Spring Boot's `ClientHttpRequestFactorySettings`
- **Resilience4j** for circuit breaker, bulkhead, and retry patterns
- **Full externalization** - all settings configurable via properties/environment variables

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Ad Orchestration Service                         │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐│
│  │                    Configuration Layer                               ││
│  │                                                                      ││
│  │  HttpClientProperties (record)     Resilience4j Auto-Config         ││
│  │  ├── mlInference                   ├── CircuitBreakerRegistry       ││
│  │  │   ├── baseUrl                   ├── BulkheadRegistry             ││
│  │  │   ├── connectTimeout            └── RetryRegistry                ││
│  │  │   └── readTimeout                                                ││
│  │  └── partner                                                        ││
│  │      ├── baseUrl                                                    ││
│  │      ├── connectTimeout                                             ││
│  │      └── readTimeout                                                ││
│  └─────────────────────────────────────────────────────────────────────┘│
│                                    │                                     │
│  ┌─────────────────────────────────▼───────────────────────────────────┐│
│  │                      Client Layer                                    ││
│  │                                                                      ││
│  │  MlInferenceClient              PartnerClient                       ││
│  │  └── RestClient                 ├── RestClient                      ││
│  │      └── JdkClientHttpRequest   ├── CircuitBreaker (per partner)    ││
│  │          Factory (with timeout) ├── Bulkhead (per partner)          ││
│  │                                 ├── Retry (per partner)             ││
│  │                                 └── JdkClientHttpRequestFactory     ││
│  └─────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────┘
```

## Configuration

### Type-Safe Properties (HttpClientProperties.java)

```java
@ConfigurationProperties(prefix = "http.client")
public record HttpClientProperties(
        ServiceConfig mlInference,
        ServiceConfig partner
) {
    public record ServiceConfig(
            String baseUrl,
            Duration connectTimeout,
            Duration readTimeout
    ) {}
}
```

### application.properties

```properties
# HTTP clients (type-safe configuration)
http.client.ml-inference.base-url=http://localhost:8081
http.client.ml-inference.connect-timeout=100ms
http.client.ml-inference.read-timeout=50ms

http.client.partner.base-url=http://localhost:8082
http.client.partner.connect-timeout=100ms
http.client.partner.read-timeout=50ms

# Resilience4j Circuit Breaker
resilience4j.circuitbreaker.configs.default.failure-rate-threshold=50
resilience4j.circuitbreaker.configs.default.slow-call-rate-threshold=80
resilience4j.circuitbreaker.configs.default.slow-call-duration-threshold=60ms
resilience4j.circuitbreaker.configs.default.sliding-window-type=count_based
resilience4j.circuitbreaker.configs.default.sliding-window-size=20
resilience4j.circuitbreaker.configs.default.minimum-number-of-calls=10
resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state=5s
resilience4j.circuitbreaker.configs.default.permitted-number-of-calls-in-half-open-state=3

# Resilience4j Bulkhead
resilience4j.bulkhead.configs.default.max-concurrent-calls=10
resilience4j.bulkhead.configs.default.max-wait-duration=0ms

# Resilience4j Retry
resilience4j.retry.configs.default.max-attempts=1
resilience4j.retry.configs.default.wait-duration=0ms
```

### Environment Variables (Docker)

```yaml
environment:
  - HTTP_CLIENT_ML_INFERENCE_BASE_URL=http://ml-inference:8081
  - HTTP_CLIENT_ML_INFERENCE_CONNECT_TIMEOUT=100ms
  - HTTP_CLIENT_ML_INFERENCE_READ_TIMEOUT=50ms
  - HTTP_CLIENT_PARTNER_BASE_URL=http://partner-simulator:8082
  - HTTP_CLIENT_PARTNER_CONNECT_TIMEOUT=100ms
  - HTTP_CLIENT_PARTNER_READ_TIMEOUT=50ms
```

## Client Implementation

### Using Spring Boot's RestClient.Builder

```java
@Bean
public MlInferenceClient mlInferenceClient(RestClient.Builder builder) {
    var settings = ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(properties.mlInference().connectTimeout())
            .withReadTimeout(properties.mlInference().readTimeout());

    var requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);

    var restClient = builder
            .baseUrl(properties.mlInference().baseUrl())
            .requestFactory(requestFactory)
            .build();

    return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(MlInferenceClient.class);
}
```

### PartnerClient with Resilience4j

```java
public Map<String, Object> bid(String partnerId, Map<String, Object> request) {
    var circuitBreaker = circuitBreakerRegistry.circuitBreaker(partnerId);
    var bulkhead = bulkheadRegistry.bulkhead(partnerId);
    var retry = retryRegistry.retry(partnerId);

    return retry.executeSupplier(
            () -> circuitBreaker.executeSupplier(
                    () -> bulkhead.executeSupplier(
                            () -> doHttpCall(partnerId, request))));
}
```

## Key Design Decisions

### 1. Type-Safe Configuration with Records

- Java records provide immutable, concise configuration
- `@ConfigurationProperties` enables IDE auto-completion
- Defaults specified in record compact constructor

### 2. Spring Boot Auto-Configuration

- `RestClient.Builder` is auto-configured by Spring Boot
- `ClientHttpRequestFactoryBuilder.detect()` selects the best HTTP client for the runtime
- No manual HTTP client instantiation needed

### 3. Resilience4j Spring Boot Starter

- All resilience configuration externalized to properties
- Registries auto-created from configuration
- Per-partner instances created on-demand using default config

### 4. Virtual Threads Compatibility

- `ClientHttpRequestFactoryBuilder.detect()` selects JDK HttpClient on Java 21+
- JDK HttpClient works well with virtual threads
- No connection pool tuning needed - virtual threads handle blocking I/O efficiently

## Dependencies

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
}
```

## Metrics

Metrics are automatically exposed:

| Metric | Type | Description |
|--------|------|-------------|
| `ad_partner_call_duration_seconds` | Histogram | Partner call duration with percentiles |
| `ad_partner_calls_total` | Counter | Partner calls by status |
| `resilience4j_circuitbreaker_*` | Various | Circuit breaker state and calls |
| `resilience4j_bulkhead_*` | Gauge | Bulkhead availability |

## Testing Configuration

Override properties for tests:

```properties
# test/resources/application-test.properties
http.client.ml-inference.connect-timeout=1s
http.client.ml-inference.read-timeout=1s
resilience4j.circuitbreaker.configs.default.minimum-number-of-calls=2
```
