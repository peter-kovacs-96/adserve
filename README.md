# AdServe - High-Performance Ad Serving Platform

Production-grade real-time bidding (RTB) platform demonstrating modern Java concurrency with Virtual Threads and StructuredTaskScope.

## Overview

AdServe demonstrates Java 25's Virtual Threads for efficient structured concurrency. The system orchestrates parallel service calls, real-time ML predictions, and demand partner bidding to serve targeted advertisements.

**Key Features:**
- **Concurrency:** Virtual Threads with StructuredTaskScope (Java 25 Preview)
- **Protocol:** OpenRTB 2.6 for partner communication
- **Observability:** Prometheus metrics and Grafana dashboards

## Architecture

```mermaid
graph TB
    Client[Client Request] --> Orch[Ad Orchestration Service<br/>Port 8080]

    Orch --> Phase1{Phase 1: Internal Services<br/>Parallel}

    Phase1 --> User[User Service<br/>gRPC :9090]
    Phase1 --> Segment[Segment Service<br/>gRPC :9091]
    Phase1 --> Target[Targeting Service<br/>gRPC :9092]

    User --> Phase2[Phase 2: ML Inference]
    Segment --> Phase2
    Target --> Phase2

    Phase2 --> ML[ML Inference Service<br/>REST :8081]

    ML --> Phase3{Phase 3: Partner Bidding<br/>Parallel}

    Phase3 --> P1[Nike]
    Phase3 --> P2[Adidas]
    Phase3 --> P3[Puma]
    Phase3 --> P4[...]
    Phase3 --> P10[10 Partners]

    P1 --> Partners[Partner Simulator<br/>REST :8082<br/>OpenRTB 2.6]
    P2 --> Partners
    P3 --> Partners
    P4 --> Partners
    P10 --> Partners

    Partners --> Auction[Phase 4: Auction<br/>First-Price]

    Auction --> Response[Phase 5: Response<br/>JSON]

    Response --> Client

    style Phase1 fill:#e1f5fe
    style Phase3 fill:#e1f5fe
    style Orch fill:#fff9c4
    style Response fill:#c8e6c9
```

## Request Flow

| Phase | Description | Implementation |
|-------|-------------|----------------|
| **1. Internal Services** | Fetch user profile, segments, and targeting rules | `StructuredTaskScope` with 3 parallel gRPC calls |
| **2. ML Prediction** | Predict CTR/CVR using request context | Sequential REST call |
| **3. Partner Bidding** | Call 10 demand partners using OpenRTB 2.6 | `StructuredTaskScope` with 10 parallel HTTP calls |
| **4. Auction** | Select highest bid (first-price auction) | In-memory comparison |
| **5. Response** | Return winning ad to client | JSON serialization |

## Technology Stack

| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| Language | Java | 25 | Virtual Threads, StructuredTaskScope (preview) |
| Framework | Spring Boot | 4.0.1 | REST API, dependency injection |
| Build Tool | Gradle | 8.x | Multi-module project management |
| Internal RPC | gRPC | 1.75.0 | High-performance service-to-service |
| Protocol Buffers | protobuf | 4.29.3 | Service contract definitions |
| RTB Protocol | OpenRTB | 2.6 | Industry standard bid request/response |
| Monitoring | Prometheus + Grafana | latest | Metrics and dashboards |

## Services

```mermaid
graph LR
    subgraph "REST Services"
        AO[ad-orchestration<br/>:8080]
        ML[ml-inference<br/>:8081]
        PS[partner-simulator<br/>:8082]
    end

    subgraph "gRPC Services"
        US[user-service<br/>:9090]
        SS[segment-service<br/>:9091]
        TS[targeting-service<br/>:9092]
    end

    subgraph "Monitoring"
        PR[prometheus<br/>:9090]
        GR[grafana<br/>:3001]
    end

    AO --> US
    AO --> SS
    AO --> TS
    AO --> ML
    AO --> PS
    PR --> AO
    GR --> PR
```

| Service | Port | Protocol | Description |
|---------|------|----------|-------------|
| ad-orchestration | 8080 | HTTP/REST | Main orchestrator, coordinates all service calls |
| user-service | 9090 | gRPC | User profile data |
| segment-service | 9091 | gRPC | Behavioral segments |
| targeting-service | 9092 | gRPC | Targeting rules |
| ml-inference | 8081 | HTTP/REST | CTR/CVR predictions (mock) |
| partner-simulator | 8082 | HTTP/REST | Simulates 10 demand partners with OpenRTB 2.6 |

## Quick Start

### Prerequisites

- Java 25
- Gradle 8.x
- Docker & Docker Compose (for full stack)

### Run with Docker Compose

```bash
# Build and start all services
docker-compose up --build

# Test the API
curl -X POST http://localhost:8080/api/v1/ads/request \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-123","deviceType":"mobile","country":"USA"}'

# View Grafana dashboards
open http://localhost:3001  # admin/admin

# Stop all services
docker-compose down
```

### Run Locally

Start each service in a separate terminal:

```bash
# Terminal 1 - User Service
./gradlew :user-service:bootRun

# Terminal 2 - Segment Service
./gradlew :segment-service:bootRun

# Terminal 3 - Targeting Service
./gradlew :targeting-service:bootRun

# Terminal 4 - ML Inference
./gradlew :ml-inference:bootRun

# Terminal 5 - Partner Simulator
./gradlew :partner-simulator:bootRun

# Terminal 6 - Ad Orchestration
./gradlew :ad-orchestration:bootRun
```

### Test Request

```bash
curl -X POST http://localhost:8080/api/v1/ads/request \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "deviceType": "mobile",
    "country": "USA"
  }'
```

### Example Response

```json
{
  "requestId": "req-abc123",
  "traceId": "trace-xyz789",
  "status": "success",
  "ad": {
    "partnerId": "nike",
    "bidPrice": 2.50,
    "adId": "ad-12345",
    "adCreativeUrl": "https://partner.com/ad.jpg"
  },
  "processingTimeMs": 87,
  "metadata": {
    "internalServicesCalled": 3,
    "mlServiceCalled": true,
    "partnersCalled": 10,
    "bidsReceived": 8
  }
}
```

## Project Structure

```
adserve/
├── ad-orchestration/       # Main orchestrator (REST API)
├── user-service/           # gRPC - User profiles
├── segment-service/        # gRPC - Behavioral segments
├── targeting-service/      # gRPC - Targeting rules
├── ml-inference/           # REST - ML predictions
├── partner-simulator/      # REST - 10 mock DSPs
├── proto/                  # Protocol Buffer definitions
├── monitoring/
│   ├── prometheus/         # Prometheus configuration
│   └── grafana/            # Grafana dashboards
├── docker-compose.yml      # Container orchestration
├── build.gradle.kts        # Root Gradle configuration
└── settings.gradle.kts     # Multi-module settings
```

## Key Implementation Details

### Virtual Threads with StructuredTaskScope

The orchestrator uses `StructuredTaskScope` for structured parallel execution:

```java
try (var scope = StructuredTaskScope.open(
        StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow(),
        cf -> cf.withTimeout(Duration.ofMillis(deadlineMs)))) {

    // Fork parallel calls to internal services
    var userTask = scope.fork(() -> callUserService(userId));
    var segmentTask = scope.fork(() -> callSegmentService(userId));
    var targetingTask = scope.fork(() -> callTargetingService(userId));

    // Wait for all tasks to complete
    scope.join();

    // Get results
    var userResponse = userTask.get();
    var segmentResponse = segmentTask.get();
    var targetingResponse = targetingTask.get();
}
```

### Benefits of This Approach

| Feature | Benefit |
|---------|---------|
| **Virtual Threads** | Lightweight threads for efficient I/O, threads unmount during blocking operations |
| **StructuredTaskScope** | Clear parent-child relationships, automatic cleanup with try-with-resources |
| **Built-in Timeouts** | Configurable deadline enforcement (10ms for gRPC, 80ms for partners) |
| **Short-Circuiting** | Built-in cancellation when any subtask fails |

## Resilience & Timeout Architecture

### StructuredTaskScope and Timeouts

When using `StructuredTaskScope`, the `close()` method (from try-with-resources) **waits for all forked tasks to complete** - this is by design for structured concurrency to prevent leaked tasks.

**Critical Rule:** HTTP timeouts must be shorter than StructuredTaskScope timeout.

```
HTTP connect + read timeout  <  StructuredTaskScope timeout
```

If HTTP timeouts exceed the scope timeout, `close()` blocks waiting for HTTP calls to finish, causing latency spikes under load.

### Circuit Breaker

Each partner has an independent circuit breaker (Resilience4j) to isolate failures:

```
CLOSED ──────────────> OPEN ──────────────> HALF_OPEN
         (failures          (wait period)        │
          exceed             expires)            │
         threshold)                              │
    ▲                                            │
    └────────────────────────────────────────────┘
                    (test calls succeed)
```

- **CLOSED:** Calls pass through normally
- **OPEN:** Calls fail immediately without making HTTP request
- **HALF_OPEN:** Limited test calls to check if partner recovered

The circuit opens when failure rate or slow-call rate exceeds configured thresholds.

### Bulkhead

Limits concurrent calls per partner to prevent resource exhaustion:

- Each partner has a max concurrent call limit
- Excess calls fail immediately (no queuing)
- Isolates slow partners from affecting others

### Request Flow with Resilience

```
Partner Call
    │
    ▼
┌─────────┐     ┌──────────────────┐     ┌──────────┐     ┌───────────┐
│  Retry  │ ──▶ │  Circuit Breaker │ ──▶ │ Bulkhead │ ──▶ │ HTTP Call │
└─────────┘     └──────────────────┘     └──────────┘     └───────────┘
```

All resilience settings are configurable via `application.properties`. See the file for current values.

## Development

### Build All Services

```bash
./gradlew build
```

### Run Tests

```bash
./gradlew test
```

### Generate gRPC Stubs

```bash
./gradlew generateProto
```

### Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/ads/request` | POST | Serve ad request |
| `/actuator/health` | GET | Health check |
| `/actuator/prometheus` | GET | Prometheus metrics |