# AdServe Technical Plan — PoC to Production

## Overview

This document is the technical counterpart to [business-plan.md](business-plan.md). It details
what to implement, how, in what order, and with what protocols/standards. Each section maps to
the business plan phases: MVP, Growth, and Moat.

## Current Architecture

```
                         ┌─────────────────────────────────────────────────────┐
                         │                 ad-orchestration :8080              │
                         │    AdController.requestAd()                        │
                         │                                                     │
    HTTP POST            │  Phase 1: StructuredTaskScope (fail-fast)          │
  /api/v1/ads/request ──>│    ├── user-service     :9090 (gRPC)              │
                         │    ├── segment-service   :9091 (gRPC)              │
                         │    └── targeting-service  :9092 (gRPC)              │
                         │                                                     │
                         │  Phase 2: ml-inference    :8081 (HTTP)              │
                         │                                                     │
                         │  Phase 3: StructuredTaskScope (partial-results)    │
                         │    ├── nike      ─┐                                 │
                         │    ├── adidas    ─┤ partner-simulator :8082 (HTTP) │
                         │    └── northface ─┘   OpenRTB 2.6 bid response     │
                         │                                                     │
                         │  Phase 4: First-price auction → winner              │
                         │  Phase 5: Build AdResponse                         │
                         └─────────────────────────────────────────────────────┘
```

**Stack**: Java 25 preview, Spring Boot 4.0.2, Spring Framework 7, Spring gRPC 1.0.2,
Virtual Threads, StructuredTaskScope, ZGC, Micrometer + Prometheus + Grafana.

**Management ports** (isolated from business traffic): 8090, 9011, 9012, 9013, 8091, 8092.

---

## Phase 1 — MVP

### 1.1 Full OpenRTB 2.6 Compliance

**What**: Replace all `Map<String, Object>` with typed Java records matching the OpenRTB 2.6 spec.

**Why**: DSPs reject bid requests that don't conform. Typed records also eliminate runtime
`ClassCastException` risks and enable Jackson schema validation.

**Module**: `ad-orchestration` (new package `io.adserve.orchestration.openrtb`)

#### OpenRTB 2.6 Object Model (Java Records)

**Bid Request** (what we send TO DSPs):

```java
// Top-level request — one per ad opportunity
record BidRequest(
    String id,                  // Auction ID (UUID)
    List<Imp> imp,              // Impression objects (at least one)
    Site site,                  // Web context (or App for mobile)
    Device device,              // User's device info
    User user,                  // User info (first-party, privacy-safe)
    Source source,              // SupplyChain (schain) for transparency
    Regs regs,                  // Privacy regulations (GDPR, CCPA, COPPA)
    int at,                     // Auction type: 1=first-price, 2=second-price
    int tmax,                   // Max response time in ms (e.g., 100)
    List<String> cur,           // Accepted currencies: ["USD"]
    List<String> bcat,          // Blocked advertiser categories
    List<String> badv           // Blocked advertiser domains
) {}

// Single ad placement being auctioned
record Imp(
    String id,                  // Impression ID
    Banner banner,              // Banner format (or Video, Native, Audio)
    double bidfloor,            // Minimum bid price (USD)
    String bidfloorcur,         // Floor currency
    Pmp pmp,                    // Private marketplace deals (Phase 3)
    int secure,                 // 1 = HTTPS required
    List<Metric> metric         // Viewability, completion rate hints
) {}

record Banner(
    int w, int h,               // Primary dimensions (e.g., 728x90)
    List<Format> format,        // Alternative accepted sizes
    List<Integer> btype,        // Blocked creative types
    List<Integer> battr,        // Blocked creative attributes
    int pos                     // Ad position (above fold=1, below=3)
) {}

record Site(
    String id,                  // Publisher-assigned site ID
    String domain,              // Publisher domain (e.g., "coolgame.io")
    String page,                // Full page URL
    String ref,                 // Referrer URL
    List<String> cat,           // IAB content categories
    Publisher publisher          // Publisher info
) {}

record Publisher(
    String id,                  // Our publisher ID (pub-12345)
    String name,
    String domain
) {}

record Device(
    String ua,                  // User-Agent string
    Geo geo,                    // Geolocation
    String ip,                  // IPv4 (truncated for privacy)
    String devicetype,          // 1=mobile, 2=PC, 3=connected TV
    String os,
    String osv,
    String language,
    int js,                     // JavaScript support: 1=yes
    int w, int h                // Screen dimensions
) {}

record Geo(
    double lat, double lon,     // Approximate (rounded for privacy)
    String country,             // ISO 3166-1 alpha-3
    String region,
    String city,
    int type                    // 1=GPS, 2=IP-derived
) {}

record User(
    String id,                  // Our first-party user ID (hashed)
    List<Data> data,            // First-party segments
    Eids eids,                  // Extended IDs (UID 2.0, ID5, etc.)
    String consent              // TCF 2.2 consent string
) {}

record Data(
    String id,                  // Data provider ID (us)
    String name,                // Provider name
    List<Segment> segment       // Segments: [{id, name, value}]
) {}

// SupplyChain for transparency (ads.txt/sellers.json)
record Source(
    Schain schain               // Supply chain object
) {}

record Schain(
    int complete,               // 1 = all nodes in chain are listed
    List<SchainNode> nodes,     // Each intermediary
    String ver                  // "1.0"
) {}

record SchainNode(
    String asi,                 // Seller ID (our domain: "adserve.io")
    String sid,                 // Publisher seat ID
    int hp,                     // 1 = direct, 0 = indirect
    String rid                  // Request ID for this hop
) {}

// Privacy/regulation signals
record Regs(
    int coppa,                  // 1 = COPPA applies (children's content)
    Ext ext                     // Extensions for GDPR/CCPA
) {}
```

**Bid Response** (what we receive FROM DSPs — already partially implemented):

```java
record BidResponse(
    String id,                  // Matches BidRequest.id
    List<SeatBid> seatbid,
    String cur                  // Response currency
) {}

record SeatBid(
    String seat,                // Bidder seat ID
    List<Bid> bid
) {}

record Bid(
    String id,                  // Bid ID
    String impid,               // Matches Imp.id
    double price,               // Bid price (CPM)
    String adid,                // Ad ID
    String adm,                 // Ad markup (HTML/VAST)
    List<String> adomain,       // Advertiser domains
    String crid,                // Creative ID
    int w, int h,               // Creative dimensions
    String nurl,                // Win notice URL
    String lurl,                // Loss notice URL
    String burl                 // Billing notice URL
) {}
```

**Files to change**:
- New: `ad-orchestration/.../openrtb/` — all records above
- Modify: `AdController.java` — use typed records instead of `Map<String, Object>`
- Modify: `partner-simulator/BidController.java` — return typed `BidResponse`
- Add: Jackson annotations for OpenRTB field naming (`@JsonProperty` where Java naming differs)

**Key spec details**:
- `at=1` for first-price auction (industry standard since 2019)
- `tmax` should match our `http.client.read-timeout` (85ms) minus overhead (~70ms)
- `source.schain` is REQUIRED by most DSPs — without it, bids are suppressed
- `regs.ext.gdpr` and `user.consent` must be populated when user is in EU
- All `id` fields should be UUIDs

**Reference**: https://github.com/InteractiveAdvertisingBureau/openrtb2.x/blob/main/2.6.md

---

### 1.2 Publisher Registration & API Keys

**What**: New `publisher-service` module. Publishers register, get an API key, manage their
ad slots. API key authenticates SDK requests.

**Why**: We need to know which publisher is making an ad request (for billing, ads.txt, reporting).

**Security**: Spring Security 7 with API key authentication.

#### Data Model

```java
record PublisherAccount(
    String publisherId,         // "pub-" + UUID (e.g., "pub-a1b2c3d4")
    String name,                // "Cool Game Studio"
    String email,
    String domain,              // "coolgame.io" (verified via ads.txt)
    String apiKey,              // Hashed (BCrypt), displayed once on creation
    boolean adsTxtVerified,     // true after we crawl and verify
    Instant createdAt,
    AccountStatus status        // PENDING, ACTIVE, SUSPENDED
) {}

record AdSlot(
    String slotId,              // "slot-" + UUID
    String publisherId,         // Owner
    String name,                // "top-banner", "rewarded-video"
    AdFormat format,            // BANNER, VIDEO, NATIVE, REWARDED
    int width, int height,      // Dimensions (banner)
    double floorPrice,          // Minimum CPM this slot accepts
    boolean active
) {}
```

#### Spring Security 7 Configuration

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(auth -> auth
                // Ad request endpoint — authenticated by API key
                .requestMatchers("/api/v1/ads/request").authenticated()
                // Impression/click beacons — public (called from user browsers)
                .requestMatchers("/api/v1/event/**").permitAll()
                // Publisher management API — authenticated
                .requestMatchers("/api/v1/publisher/**").authenticated()
                .anyRequest().denyAll()
            )
            .addFilterBefore(apiKeyAuthFilter(), UsernamePasswordAuthenticationFilter.class)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())  // Stateless API, no CSRF needed
            .build();
    }

    // Management port (8090) — no auth, internal network only
    @Bean
    @Order(0)
    SecurityFilterChain managementFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .build();
    }
}
```

**API key flow**:
1. Publisher registers via `/api/v1/publisher/register` (email + domain)
2. We generate API key, return it once (publisher stores it)
3. SDK includes API key in every request: `Authorization: Bearer <api-key>`
4. `ApiKeyAuthFilter` validates, loads publisher context, sets `SecurityContext`
5. API key is hashed in DB (BCrypt) — we never store plaintext

**Performance consideration**: At 300K RPS, we can't hit a DB per request. API key lookup
must be cached in-memory with a short TTL (e.g., Caffeine cache, 60s TTL).

**New dependencies**:
- `spring-boot-starter-security`
- `spring-boot-starter-data-jpa` (or R2DBC) + PostgreSQL
- `com.github.ben-manes.caffeine:caffeine` (API key cache)

---

### 1.3 Publisher JavaScript SDK

**What**: A lightweight JS file that publishers embed. It requests ads, renders them in
sandboxed iframes, and fires impression/click beacons.

**Delivery**: Served from CDN (CloudFront/Fastly), not from our ad-serving infrastructure.

#### SDK Architecture

```
publisher-page.html
  └── <script src="https://cdn.adserve.io/sdk/v1/adserve.js?pub=pub-12345">
        │
        ├── Collects context (URL, referrer, viewport, device)
        ├── Finds all <div data-ad-slot="..."> elements
        ├── POST /api/v1/ads/request (with API key + context)
        │     └── Returns: { ad: { adm, width, height }, impressionUrl, clickUrl }
        ├── Creates sandboxed <iframe> per slot
        │     └── Renders creative (adm) inside iframe
        ├── IntersectionObserver watches iframe visibility
        │     └── When 50% visible for 1s → fires GET impressionUrl (beacon)
        └── Click handler
              └── On click → fires GET clickUrl (beacon) → redirect to landing page
```

#### Key Technical Details

**Sandboxed iframe** (security — ad creative is untrusted third-party code):
```html
<iframe sandbox="allow-scripts allow-popups allow-popups-to-escape-sandbox"
        srcdoc="<html>...creative markup...</html>"
        width="728" height="90"
        style="border:none;">
</iframe>
```
- `sandbox` prevents creative from accessing publisher's DOM, cookies, or JS context
- `allow-scripts` lets the creative run its own JS (needed for interactivity)
- `allow-popups` lets click-through open landing pages
- No `allow-same-origin` — creative is isolated from publisher

**Viewability tracking** (IAB MRC standard — determines when an impression is billable):
```javascript
const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
        if (entry.intersectionRatio >= 0.5) {
            // 50% visible — start 1-second timer
            startViewabilityTimer(entry.target);
        } else {
            cancelViewabilityTimer(entry.target);
        }
    });
}, { threshold: 0.5 });
```
- Display ad: 50% of pixels visible for >= 1 continuous second
- Video ad: 50% of pixels visible for >= 2 continuous seconds
- Only fire impression beacon when viewability threshold is met

**SDK size target**: < 15KB gzipped (compare: Google AdSense JS is ~30KB).

---

### 1.4 Impression & Click Tracking

**What**: Server-side endpoints that record ad events (impression viewed, ad clicked).
These are the foundation of billing — no tracking = no revenue.

**Module**: `ad-orchestration` (new endpoints on business port)

#### Endpoints

```
GET /api/v1/event/imp?auction={auctionId}&imp={impId}&pub={pubId}&ts={timestamp}&sig={signature}
GET /api/v1/event/click?auction={auctionId}&imp={impId}&pub={pubId}&ts={timestamp}&sig={signature}
```

- **GET** not POST — browser fires these as image beacons (`<img src="...">`) or `navigator.sendBeacon()`
- Must return `204 No Content` + `Cache-Control: no-store` (never cache event beacons)
- `sig` is HMAC-SHA256 of the other params with a server-side secret — prevents forged impressions

#### Event Processing Architecture

```
Browser fires beacon
  → GET /api/v1/event/imp (< 5ms response, fire-and-forget)
  → Validate signature (HMAC check)
  → Deduplicate (check auction+imp combo in Bloom filter or Redis SET)
  → Write to append-only event log:
      Option A: Kafka topic "impressions" (if we add Kafka)
      Option B: In-memory bounded buffer → periodic batch flush to DB/S3
      Option C: Direct async DB insert (simplest for MVP)
```

**Performance**: Event endpoints must be as fast as possible. They're called by the user's
browser — any latency delays the ad experience. Target: < 5ms p99.

**Deduplication**: The same impression beacon might fire multiple times (browser retries,
viewability re-triggers). Deduplicate by `auctionId + impId` with a 1-hour TTL window.

**No auth on event endpoints**: These are called from user browsers (not the publisher's
server). Authentication is via the HMAC signature in the URL parameters.

#### Win Notice

After auction completes, we must call the winning bidder's `nurl` (win notice URL):
```java
// After selecting winner, fire win notice asynchronously
Thread.startVirtualThread(() -> {
    var winUrl = winner.nurl()
        .replace("${AUCTION_PRICE}", String.valueOf(winner.price()))
        .replace("${AUCTION_ID}", auctionId);
    httpClient.send(HttpRequest.newBuilder(URI.create(winUrl)).GET().build(),
        HttpResponse.BodyHandlers.discarding());
});
```
- `${AUCTION_PRICE}` and `${AUCTION_ID}` are OpenRTB macro substitutions
- Fire-and-forget on a virtual thread — don't block the response
- DSPs use win notices to track their spend and train bidding algorithms

#### Loss Notice

Notify losing bidders why they lost (optional but builds trust with DSPs):
```java
// Loss reason codes (OpenRTB 2.6 Section 5.25)
// 1 = Internal Error, 2 = Impression Opportunity Expired
// 3 = Invalid Bid Response, 4 = Invalid Deal ID
// 100 = Bid Was Below Auction Floor, 102 = Lost to Higher Bid
```

---

### 1.5 ads.txt & sellers.json

**What**: Industry-standard transparency files that prove we're authorized to sell a
publisher's inventory. Without these, DSPs suppress bids.

#### ads.txt (publisher-side)

Publisher places a text file at `https://coolgame.io/ads.txt`:
```
# ads.txt — Authorized Digital Sellers
# <exchange domain>, <seller account ID>, <relationship>, <cert authority ID>
adserve.io, pub-a1b2c3d4, DIRECT, f08c47fec0942fa0
```

**Our responsibilities**:
1. On publisher registration, instruct them to add the ads.txt entry
2. Background job crawls `https://{publisher.domain}/ads.txt` daily
3. Verify our entry exists with correct publisher ID
4. Set `publisher.adsTxtVerified = true` when confirmed
5. DSPs cross-reference our bid requests against publisher ads.txt files

**Implementation**: `@Scheduled` task in publisher-service:
```java
@Scheduled(cron = "0 0 3 * * *")  // Daily at 3 AM
public void verifyAdsTxt() {
    publisherRepository.findAllActive().forEach(pub -> {
        var adsTxt = httpClient.send(
            HttpRequest.newBuilder(URI.create("https://" + pub.domain() + "/ads.txt")).build(),
            HttpResponse.BodyHandlers.ofString()
        );
        var verified = adsTxt.body().contains("adserve.io, " + pub.publisherId());
        publisherRepository.updateAdsTxtStatus(pub.publisherId(), verified);
    });
}
```

#### sellers.json (our side)

We host a JSON file at `https://adserve.io/sellers.json`:
```json
{
  "contact_email": "adops@adserve.io",
  "contact_address": "...",
  "version": "1.0",
  "identifiers": [
    { "name": "TAG-ID", "value": "f08c47fec0942fa0" }
  ],
  "sellers": [
    {
      "seller_id": "pub-a1b2c3d4",
      "seller_type": "PUBLISHER",
      "name": "Cool Game Studio",
      "domain": "coolgame.io",
      "is_confidential": 0
    }
  ]
}
```

**Implementation**: Auto-generated from publisher database. Served as a static JSON endpoint.
Updated on publisher registration/removal.

#### SupplyChain (schain)

Included in every OpenRTB bid request `source.schain`:
```json
{
  "complete": 1,
  "ver": "1.0",
  "nodes": [
    {
      "asi": "adserve.io",
      "sid": "pub-a1b2c3d4",
      "hp": 1,
      "rid": "auction-uuid"
    }
  ]
}
```
- `complete=1` means we're the only intermediary (direct relationship)
- `hp=1` means payment flows directly through us
- DSPs verify this against `ads.txt` and `sellers.json`

---

### 1.6 Reporting API

**What**: REST API for publishers to query their earnings, impressions, CPMs, fill rates.

**Endpoints**:
```
GET /api/v1/publisher/report/summary?from=2026-02-01&to=2026-02-10
GET /api/v1/publisher/report/by-slot?from=...&to=...
GET /api/v1/publisher/report/by-day?from=...&to=...
```

**Response example**:
```json
{
  "publisherId": "pub-a1b2c3d4",
  "period": { "from": "2026-02-01", "to": "2026-02-10" },
  "impressions": 1420000,
  "clicks": 2840,
  "ctr": 0.002,
  "revenue": 8265.00,
  "avgCpm": 5.82,
  "fillRate": 0.87,
  "bySlot": [
    { "slotId": "slot-001", "name": "top-banner", "impressions": 890000, "revenue": 5180.00 },
    { "slotId": "slot-002", "name": "sidebar", "impressions": 530000, "revenue": 3085.00 }
  ]
}
```

**Data source**: Aggregated from impression/click event log. Pre-computed hourly/daily
rollups to avoid scanning raw events on every query.

---

### 1.7 Database

**What**: Persistent storage for publisher accounts, ad slots, events, and reporting.

**Choice**: PostgreSQL (reliable, well-supported by Spring Data JPA).

**Schema (core tables)**:

```sql
-- Publisher accounts
CREATE TABLE publishers (
    publisher_id    VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) UNIQUE NOT NULL,
    domain          VARCHAR(255) NOT NULL,
    api_key_hash    VARCHAR(255) NOT NULL,
    ads_txt_verified BOOLEAN DEFAULT FALSE,
    status          VARCHAR(20) DEFAULT 'PENDING',
    created_at      TIMESTAMP DEFAULT NOW()
);

-- Ad slot definitions
CREATE TABLE ad_slots (
    slot_id         VARCHAR(36) PRIMARY KEY,
    publisher_id    VARCHAR(36) REFERENCES publishers(publisher_id),
    name            VARCHAR(255) NOT NULL,
    format          VARCHAR(20) NOT NULL,       -- BANNER, VIDEO, NATIVE, REWARDED
    width           INT,
    height          INT,
    floor_price     DECIMAL(10,4) DEFAULT 0.0,
    active          BOOLEAN DEFAULT TRUE
);

-- Raw impression/click events (append-only, high volume)
CREATE TABLE events (
    event_id        BIGSERIAL PRIMARY KEY,
    auction_id      VARCHAR(36) NOT NULL,
    imp_id          VARCHAR(36) NOT NULL,
    publisher_id    VARCHAR(36) NOT NULL,
    slot_id         VARCHAR(36),
    event_type      VARCHAR(10) NOT NULL,       -- IMPRESSION, CLICK
    winning_partner VARCHAR(50),
    price           DECIMAL(10,6),              -- Clearing price (CPM)
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE (auction_id, imp_id, event_type)     -- Deduplication
);
-- Partition by month for performance: PARTITION BY RANGE (created_at)

-- Pre-aggregated daily reports
CREATE TABLE daily_reports (
    publisher_id    VARCHAR(36) NOT NULL,
    slot_id         VARCHAR(36),
    report_date     DATE NOT NULL,
    impressions     BIGINT DEFAULT 0,
    clicks          BIGINT DEFAULT 0,
    revenue         DECIMAL(12,4) DEFAULT 0.0,
    PRIMARY KEY (publisher_id, slot_id, report_date)
);
```

**Performance notes**:
- `events` table will grow fast (300K rows/sec at scale) — partition by month, archive to S3
- At MVP scale (10M impressions/month), PostgreSQL handles this fine
- At Growth scale (10B/month), migrate events to ClickHouse or BigQuery
- `daily_reports` is small and fast — pre-computed by a scheduled aggregation job
- API key lookups cached in Caffeine (never hit DB on the hot path)

**New module or integrated**: For MVP, add PostgreSQL to `ad-orchestration` via
`spring-boot-starter-data-jpa`. Extract to a separate `publisher-service` later
when the codebase warrants it.

**docker-compose addition**:
```yaml
postgres:
  image: postgres:17-alpine
  environment:
    POSTGRES_DB: adserve
    POSTGRES_USER: adserve
    POSTGRES_PASSWORD: adserve
  ports:
    - "5432:5432"
  volumes:
    - pgdata:/var/lib/postgresql/data
  healthcheck:
    test: ["CMD", "pg_isready", "-U", "adserve"]
    interval: 10s
    timeout: 5s
    retries: 5
```

---

## Phase 2 — Growth

### 2.1 Real DSP Integration

**What**: Replace partner-simulator with connections to real DSPs.

**How**: Our server sends OpenRTB 2.6 bid requests to DSP endpoints. Each DSP provides
an HTTP endpoint URL during onboarding.

**Architecture change**:
```
Current:  ad-orchestration → partner-simulator (localhost:8082)
Future:   ad-orchestration → DSP bidder endpoints (external HTTPS)
```

**DSP onboarding requirements** (what each DSP needs from us):
1. OpenRTB 2.6 compliant bid requests (Section 1.1 of this doc)
2. `ads.txt` + `sellers.json` + `schain` (Section 1.5)
3. Minimum QPS commitment (usually 1K-100K QPS)
4. IVT/fraud rate below 2% (need fraud detection first)
5. Geographic coverage info (which regions our publishers serve)

**Target DSPs for first integrations**:
| DSP | OpenRTB Version | Notes |
|-----|-----------------|-------|
| Google Authorized Buyers (AdX) | 2.6 + extensions | Largest demand, strict compliance |
| The Trade Desk | 2.5/2.6 | ~20% take rate, large spend |
| Xandr (Microsoft) | 2.6 | Good documentation |
| Amazon DSP | 2.6 | Growing fast |

**Timeout handling**: DSPs must respond within `tmax` (70ms). Use the existing
`StructuredTaskScope` partial-results pattern — if a DSP times out, proceed with
available bids.

**QPS management**: DSPs rate-limit SSPs. We must respect their limits:
- Per-DSP rate limiter (e.g., Resilience4j or Guava RateLimiter)
- If over limit, skip that DSP for this auction (don't block)

---

### 2.2 Prebid.js Header Bidding Adapter

**What**: Prebid.js is the industry-standard open-source header bidding wrapper. Publishers
already using Prebid can add us as one of their SSP sources.

**Why**: Fastest path to publisher adoption. Publishers don't need our SDK — they add
a config block to their existing Prebid setup.

**Publisher integration** (their side):
```javascript
pbjs.addAdUnits({
    code: 'top-banner',
    mediaTypes: { banner: { sizes: [[728, 90], [970, 250]] } },
    bids: [{
        bidder: 'adserve',
        params: {
            publisherId: 'pub-a1b2c3d4',
            slotId: 'slot-001'
        }
    }]
});
```

**Our side**: Implement a Prebid.js bidder adapter (JavaScript module submitted to the
Prebid.js open-source project):
- `buildRequests()` — constructs OpenRTB bid request from Prebid ad unit data
- `interpretResponse()` — parses our OpenRTB bid response into Prebid bid objects
- `getUserSyncs()` — optional cookie sync (for first-party ID matching)

**Server endpoint**: Same `/api/v1/ads/request` but Prebid sends requests in its format.
Either we accept Prebid's format directly or the adapter translates to OpenRTB.

**Reference**: https://docs.prebid.org/dev-docs/bidder-adaptor.html

---

### 2.3 VAST Video Ad Support

**What**: Support video ads via VAST (Video Ad Serving Template) XML responses.

**Why**: Video CPMs are 3-5x higher than display ($15-$30 vs $2-$10).

**How it works**:
1. `Imp.video` in bid request signals video placement
2. DSP returns VAST XML in `bid.adm`:
```xml
<VAST version="4.2">
  <Ad id="ad-123">
    <InLine>
      <AdSystem>PartnerDSP</AdSystem>
      <AdTitle>Nike Running</AdTitle>
      <Impression><![CDATA[https://adserve.io/api/v1/event/imp?...]]></Impression>
      <Creatives>
        <Creative>
          <Linear>
            <Duration>00:00:15</Duration>
            <MediaFiles>
              <MediaFile type="video/mp4" width="640" height="360">
                https://cdn.partner.com/video.mp4
              </MediaFile>
            </MediaFiles>
          </Linear>
        </Creative>
      </Creatives>
    </InLine>
  </Ad>
</VAST>
```
3. Our SDK passes VAST to a video player (HTML5 `<video>` element)
4. Video player fires impression/tracking events at quartile milestones

**OpenRTB Video object** (added to Imp):
```java
record Video(
    List<String> mimes,         // ["video/mp4", "video/webm"]
    int minduration,            // Min duration in seconds
    int maxduration,            // Max duration (e.g., 30)
    List<Integer> protocols,    // VAST versions supported
    int w, int h,               // Player dimensions
    int linearity,              // 1=linear (pre-roll), 2=non-linear (overlay)
    int placement,              // 1=in-stream, 2=in-banner, 3=in-article
    int plcmt                   // 2.6 field: 1=instream, 2=accompanying, 3=interstitial, 4=standalone
) {}
```

---

### 2.4 Fraud Detection (Invalid Traffic Filtering)

**What**: Detect and filter bot traffic, ad stacking, click fraud, and other invalid traffic.

**Why**: DSPs track IVT rates per SSP. If our rate exceeds ~2%, they reduce or stop bidding.
Advertisers demand this.

**Implementation layers**:

1. **Pre-bid filtering** (in ad request path, must be fast):
   - IP reputation check (known bot IPs, data center IPs)
   - User-Agent validation (detect headless browsers, known bots)
   - Rate limiting per IP (no human clicks 100 ads/sec)
   - Device fingerprint consistency

2. **Post-impression analysis** (async, batch):
   - Click-through rate anomalies (CTR > 5% is suspicious)
   - Geographic mismatch (IP says US, timezone says Russia)
   - Session pattern analysis (bot-like navigation)

3. **Third-party verification** (Phase 3, outsource):
   - DoubleVerify, IAS, or MOAT integration
   - Industry-standard IVT classification (GIVT = general, SIVT = sophisticated)

**Pre-bid filter implementation** (add to ad request pipeline):
```java
// Must complete in < 1ms — do NOT add to hot path if too slow
public boolean isValidTraffic(AdRequest request, HttpServletRequest http) {
    var ip = http.getRemoteAddr();
    if (ipReputationCache.isDataCenter(ip)) return false;
    if (ipReputationCache.isKnownBot(ip)) return false;
    if (rateLimiter.isOverLimit(ip, 10, Duration.ofSeconds(1))) return false;
    if (isHeadlessBrowser(http.getHeader("User-Agent"))) return false;
    return true;
}
```

---

### 2.5 Publisher Payment System

**What**: Actually pay publishers their earnings (minus our take rate).

**Payment flow**:
1. Impression events accumulate in `daily_reports`
2. Monthly invoice generated: `total_revenue - (total_revenue * take_rate)`
3. Payment via Stripe Connect or PayPal Payouts
4. Minimum payout threshold: $50 (avoid micro-payments)
5. Payment terms: NET 60 (DSPs pay us NET 30-60, we need buffer)

**Stripe Connect integration**:
- Publisher connects their Stripe account during onboarding
- We use Stripe's `Transfer` API for payouts
- Stripe handles tax forms (1099 for US publishers)

---

## Phase 3 — Moat

### 3.1 ML Bid Floor Optimization

**What**: Use ml-inference to dynamically set bid floors per impression.

**Current**: Publishers set a static `floorPrice` per ad slot.

**Target**: ML model predicts the optimal floor for each impression based on:
- Historical clearing prices for this slot
- Time of day, day of week
- User segments and demographics
- Device type and geo
- DSP bid patterns

**Architecture**: Extend ml-inference to expose a `/api/v1/floor` endpoint:
```
Input:  { slotId, segments, device, geo, dayOfWeek, hourOfDay }
Output: { suggestedFloor: 3.75 }
```
Called from ad-orchestration before sending bid requests. Must be < 5ms.

### 3.2 Private Marketplace (PMP)

**What**: Direct deals between specific publishers and advertisers at negotiated terms.

**OpenRTB support**: `Imp.pmp` object:
```java
record Pmp(
    int private_auction,        // 1 = bids restricted to deals
    List<Deal> deals
) {}

record Deal(
    String id,                  // Deal ID (shared between buyer and seller)
    double bidfloor,            // Deal-specific floor
    String bidfloorcur,
    int at,                     // Auction type for this deal
    List<String> wseat          // Allowed buyer seats
) {}
```

---

## Legal & Privacy Compliance — Technical Requirements

### GDPR (EU)

**Requirement**: Explicit consent before processing personal data for ad targeting.

**Implementation**:
1. SDK checks for Consent Management Platform (CMP) on publisher's page
2. Reads TCF 2.2 consent string from `__tcfapi` JavaScript API
3. Passes consent in OpenRTB: `user.consent = "CPXxRfAPXxRfAAfKABENB-CgAAAAAAAAAAYgAAAAAAAA"`
4. Passes GDPR flag: `regs.ext.gdpr = 1`
5. If no consent: strip `user.id`, `device.ifa`, all behavioral segments
6. Contextual-only targeting for non-consented users (still monetizable at lower CPM)

**Key principle**: We MUST NOT send user identifiers to DSPs without valid consent.

### CCPA/CPRA (California)

**Requirement**: Consumers can opt out of personal data sale.

**Implementation**:
1. SDK checks for US Privacy string: `__uspapi`
2. Passes in OpenRTB: `regs.ext.us_privacy = "1YNN"` (format: version, notice, opt-out, LSPA)
3. If opted out: same as GDPR no-consent — contextual only

### COPPA (Children's Content)

**Requirement**: No behavioral targeting on content directed at children under 13.

**Implementation**:
1. Publisher declares `coppa=true` on their ad slots (during registration)
2. Bid request sets `regs.coppa = 1`
3. Strip all user data, segments, device IDs
4. DSPs must respond with COPPA-compliant creatives

### TCF 2.2 Integration

The IAB Transparency & Consent Framework is the technical standard for GDPR compliance
in ad tech.

**SDK-side** (JavaScript):
```javascript
// Check if CMP is available
if (window.__tcfapi) {
    window.__tcfapi('getTCData', 2, (tcData, success) => {
        if (success) {
            adRequest.gdpr = 1;
            adRequest.consent = tcData.tcString;
            adRequest.gdprApplies = tcData.gdprApplies;
        }
    });
}
```

**Server-side**: Parse TCF consent string to determine:
- Which vendors have consent (we need to be registered as a vendor with IAB)
- Which purposes are consented (purpose 1 = store/access info, purpose 2 = basic ads)
- Pass to DSPs in bid request — they check their own vendor consent

**IAB vendor registration**: We must register as a Global Vendor List (GVL) vendor to
participate in TCF. This is a business/legal step, not just technical.

---

## Infrastructure for Scale

### Deployment Architecture (target)

```
                    ┌──────────────┐
                    │   CDN        │
                    │ (SDK JS,     │
                    │  static)     │
                    └──────┬───────┘
                           │
                    ┌──────┴───────┐
                    │ Load Balancer │
                    │ (L7, TLS)    │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
     ┌────────┴──┐  ┌─────┴────┐  ┌───┴────────┐
     │ad-orch #1 │  │ad-orch #2│  │ad-orch #N  │
     │  :8080    │  │  :8080   │  │  :8080     │
     └─────┬─────┘  └────┬─────┘  └─────┬──────┘
           │              │              │
     ┌─────┴──────────────┴──────────────┴──────┐
     │        Internal Service Mesh              │
     │  user-svc  segment-svc  targeting-svc     │
     │  ml-inference  (all via gRPC/HTTP)        │
     └───────────────────┬──────────────────────┘
                         │
              ┌──────────┴──────────┐
              │                     │
     ┌────────┴──┐          ┌──────┴───────┐
     │ PostgreSQL │          │ Redis/Kafka  │
     │ (accounts, │          │ (events,     │
     │  reports)  │          │  dedup,      │
     │            │          │  caching)    │
     └────────────┘          └──────────────┘
```

### Scaling Considerations

| Component | Scaling Strategy |
|-----------|-----------------|
| ad-orchestration | Horizontal (stateless, N replicas behind LB) |
| user/segment/targeting services | Horizontal (stateless gRPC services) |
| ml-inference | Horizontal (stateless HTTP) |
| PostgreSQL | Vertical first, then read replicas, then shard events table |
| Event ingestion | Kafka at Growth stage (replace in-memory buffer) |
| SDK delivery | CDN (CloudFront/Fastly) — no origin load |
| API key cache | In-process Caffeine (per instance) |
| Session/dedup | Redis cluster (shared across instances) |

### Monitoring Additions

Extend existing Prometheus + Grafana setup with business metrics:

```java
// In AdMetrics.java — add to existing class
private final LongAdder totalImpressions = new LongAdder();
private final LongAdder totalClicks = new LongAdder();

public void recordImpression(String publisherId, String partnerId, double price) {
    totalImpressions.increment();
    meterRegistry.counter("ad_impressions_total", "publisher", publisherId, "partner", partnerId).increment();
    meterRegistry.counter("ad_revenue_total", "publisher", publisherId).increment(price);
}

public void recordClick(String publisherId) {
    totalClicks.increment();
    meterRegistry.counter("ad_clicks_total", "publisher", publisherId).increment();
}
```

---

## New Module Summary

| Module | Type | Purpose | Phase |
|--------|------|---------|-------|
| `ad-orchestration` | Existing | Add Security, OpenRTB records, event endpoints, publisher API | 1 |
| `publisher-sdk` | New (JS) | JavaScript ad tag served from CDN | 1 |
| PostgreSQL | New (infra) | Publisher accounts, events, reports | 1 |
| Redis | New (infra) | API key cache, dedup, rate limiting | 1-2 |
| Kafka | New (infra) | Event streaming at scale | 2 |

---

## Build Order (Recommended)

```
Week 1-2:   OpenRTB 2.6 typed records + update AdController + partner-simulator
Week 3-4:   PostgreSQL + publisher registration + API key auth (Spring Security 7)
Week 5-6:   Impression/click tracking endpoints + HMAC signing
Week 7-8:   JavaScript SDK (ad tag, iframe rendering, viewability, beacons)
Week 9-10:  ads.txt verification + sellers.json generation + schain in bid requests
Week 11-12: Reporting API + basic publisher dashboard
Week 13+:   First real DSP integration (Prebid.js adapter or direct)
```

---

## References

- OpenRTB 2.6: https://github.com/InteractiveAdvertisingBureau/openrtb2.x/blob/main/2.6.md
- OpenRTB 2.6 PDF: https://iabtechlab.com/wp-content/uploads/2022/04/OpenRTB-2-6_FINAL.pdf
- Prebid.js Bidder Adapter: https://docs.prebid.org/dev-docs/bidder-adaptor.html
- VAST 4.2: https://iabtechlab.com/standards/vast/
- ads.txt Specification: https://iabtechlab.com/ads-txt/
- sellers.json: https://iabtechlab.com/sellers-json/
- SupplyChain (schain): https://iabtechlab.com/standards/sellers-json-supplychain/
- TCF 2.2: https://iabeurope.eu/transparency-consent-framework/
- IAB MRC Viewability Guidelines: https://www.iab.com/guidelines/iab-measurement-guidelines/
- Spring Security 7: https://docs.spring.io/spring-security/reference/
- OpenRTB Loss Reason Codes: OpenRTB 2.6 spec, Section 5.25
