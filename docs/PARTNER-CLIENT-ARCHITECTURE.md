# HTTP Client & Resilience Architecture

## Overview

This document describes the HTTP client and resilience architecture for ad-orchestration, using modern Spring Framework patterns:

- **HTTP Service Registry** with `@ImportHttpServices` for declarative, group-based HTTP client management
- **`@HttpExchange` interfaces** as type-safe HTTP service contracts
- **Spring Framework native resilience** with `@Retryable` and `@ConcurrencyLimit`
- **Virtual Thread executor** backing the shared JDK `HttpClient`

## HTTP Service Registry

HTTP service clients are organized into **groups** using `@ImportHttpServices`. Each group maps to a logical service (e.g., a specific partner or the ML inference service) and shares the same HTTP client configuration.

Groups are declared on the configuration class, each specifying which `@HttpExchange` interface(s) belong to it. Spring automatically creates and registers proxy beans for each interface.

A single `RestClientHttpServiceGroupConfigurer` bean configures all groups. It creates one shared JDK `HttpClient` with a Virtual Thread executor and applies per-group settings (base URL, read timeout) from externalized properties.

### Configuration Properties

HTTP client settings are externalized via `@ConfigurationProperties`:

- **`http.client.connect-timeout`** - Connect timeout applied to the shared `HttpClient`
- **`http.client.read-timeout`** - Read timeout applied per-group via the request factory
- **`http.client.groups.<name>`** - Base URL for each group (e.g., `http.client.groups.nike=http://localhost:8082/partners/nike`)

In Docker, these are overridden via environment variables to use container hostnames.

## Resilience

The project uses **Spring Framework native resilience** (enabled via `@EnableResilientMethods`), not Resilience4j.

### @Retryable

Each `@HttpExchange` method is annotated with `@Retryable` using externalized property placeholders for `maxRetries` and `delay`. This allows retry behavior to be tuned per environment without code changes.

Retry settings are grouped by service type in `application.properties`:
- `resilience.retry.partners.*` - Settings for all partner bid calls
- `resilience.retry.ml-inference.*` - Settings for ML inference calls

### @ConcurrencyLimit

`@ConcurrencyLimit` is available (currently commented out in the codebase) to limit concurrent calls per service method. This is particularly useful with Virtual Threads, where there is no inherent thread pool size to act as a natural throttle.

## Timeout Strategy

HTTP connect and read timeouts **must be shorter** than any `StructuredTaskScope` timeout. If they are not, `scope.close()` will block waiting for in-flight HTTP calls to complete, causing latency spikes.

The timeout hierarchy:
1. **HTTP connect timeout** (shortest) - fails fast if the target is unreachable
2. **HTTP read timeout** - fails if the target is slow to respond
3. **StructuredTaskScope timeout** (longest) - overall deadline for the parallel execution phase

## OpenRTB 2.6 Typed Model

All partner communication uses typed Java records matching the OpenRTB 2.6 specification (`io.adserve.orchestration.openrtb` package). Key design decisions:

- **`@JsonInclude(NON_NULL)`** on all records — OpenRTB requires omitting absent fields, not sending `null`
- **Boxed `Integer`/`Double`** for optional numeric fields — primitive `int` serializes as `0`, boxed `Integer` is omitted when null (critical: `"coppa": 0` means "does not apply", absent means "unknown")
- **`@JsonProperty`** only where Java naming differs from spec (`us_privacy` → `usPrivacy`, `deal_id` → `dealId`)
- **No `ext` fields** — extension objects skipped until a specific DSP requires them

Records cover the full request/response chain: `BidRequest` → `Imp`, `Banner`, `Format`, `Site`, `Publisher`, `Device`, `Geo`, `User`, `Data`, `Segment`, `Source`, `Schain`, `SchainNode`, `Regs` | `BidResponse` → `SeatBid`, `Bid`.

The partner-simulator returns `Map<String, Object>` — Jackson transparently deserializes this into the typed `BidResponse` records on the ad-orchestration side. No shared module is needed.

ML inference uses separate typed records (`MlPredictionRequest`/`MlPredictionResponse`) in the `client` package — not OpenRTB, internal protocol.

## Partner Client Registry

Partner clients all implement a common `PartnerBidClient` interface with a typed contract: `BidResponse bid(BidRequest request)`. A `PartnerClientRegistry` bean collects all implementations and provides lookup by partner ID, allowing the auction loop to iterate over partners dynamically.

Each partner has its own `@HttpExchange` interface (extending `PartnerBidClient`) and its own HTTP service group, enabling per-partner base URL and timeout configuration.