# OpenRTB 2.6 Model Reference

This document explains every record in `io.adserve.orchestration.openrtb` — what it represents, why it exists in the OpenRTB protocol, and which fields matter most.

## How OpenRTB Works

OpenRTB (Open Real-Time Bidding) is the industry-standard protocol for programmatic advertising. When a user visits a webpage with an ad slot:

1. **Publisher's ad server** sends a `BidRequest` to one or more demand partners (DSPs)
2. Each DSP evaluates the request and returns a `BidResponse` with a price
3. The highest bid wins the auction and their ad is shown

We (AdServe) sit in the middle as an SSP (Supply-Side Platform) — we build the `BidRequest`, fan it out to partners, collect `BidResponse`s, and pick the winner.

## Request Building Pipeline

The `BidRequest` is assembled by `BidRequestBuilder` from multiple data sources:

| Data Source | What It Provides |
|-------------|-----------------|
| **AdRequest** (SDK/client) | Impression specs (sizes, tagId, bidFloor, pos, btype, battr, secure, interstitial, rewarded), site/app context, device context + geo (lat/lon/city), user (consent, yob, gender), regulations, auction controls |
| **HTTP headers** | `User-Agent` → `Device.ua`, `X-Forwarded-For` / remote addr → `Device.ip` |
| **User Service** (gRPC) | Demographics (country, region) → `Geo.country/region` |
| **Segment Service** (gRPC) | Behavioral/demographic audience segments → `User.data` |
| **Server config** | Publisher identity, supply chain (schain/pchain), auction type, currency |

### Field Population Strategy

We own the SDK, so it always sends all required and recommended fields per OpenRTB 2.6. `BidRequestBuilder` maps every SDK field through to the corresponding OpenRTB object — no null values in the builder. The only nullable fields in the `BidRequest` are `site` (null when `app` is set) and `app` (null when `site` is set), which is per the OpenRTB spec (a request has one or the other, never both).

## Request-Side Records

### BidRequest

The top-level auction object. One `BidRequest` = one auction opportunity.

| Field | Type | Spec Status | Source | Purpose |
|-------|------|-------------|--------|---------|
| `id` | String | **required** | server-generated UUID | Unique auction ID. Partners echo this back in their response so we can match it. |
| `imp` | List\<Imp\> | **required** | built from AdRequest | The ad slots available in this auction. Usually one, but a page can offer multiple. |
| `site` | Site | **recommended** | AdRequest.site | Context about the webpage where the ad will appear (null when `app` is set). |
| `app` | App | **recommended** | AdRequest.app | Context about the mobile/gaming app where the ad will appear (null when `site` is set). |
| `device` | Device | **recommended** | AdRequest.device + headers + gRPC | The user's device — browser, OS, screen size, location. |
| `user` | User | **recommended** | AdRequest.userId + gRPC segments | What we know about the user — ID, segments, consent status. |
| `source` | Source | optional | server config | Supply chain info — proves we're a legitimate seller. |
| `regs` | Regs | optional | AdRequest.regs | Privacy/regulation signals — GDPR, COPPA, CCPA. |
| `at` | Integer | optional | hardcoded `1` | Auction type. `1` = first-price (winner pays what they bid). `2` = second-price. Industry moved to first-price. |
| `tmax` | Integer | optional | AdRequest.tmax (default `70`) | Maximum time in ms we'll wait for a response. Partners that respond slower get ignored. |
| `cur` | List\<String\> | optional | hardcoded `["USD"]` | Accepted currencies. |
| `bcat` | List\<String\> | optional | AdRequest.blockedCategories | Blocked advertiser categories (IAB taxonomy). Publisher says "no gambling ads". |
| `badv` | List\<String\> | optional | AdRequest.blockedAdvertisers | Blocked advertiser domains. Publisher says "no ads from competitor.com". |
| `test` | Integer | optional | AdRequest.test | `1` = test mode (non-billable). Omitted when not testing. |

**Note**: A BidRequest has **either** `site` (web inventory) or `app` (mobile/gaming inventory), never both.

### Imp (Impression)

An individual ad placement within the auction. "Impression" = one ad shown to one user.

| Field | Type | Spec Status | Source | Purpose |
|-------|------|-------------|--------|---------|
| `id` | String | **required** | hardcoded `"imp-1"` | Impression ID within this auction. |
| `banner` | Banner | at least one media type | built from AdRequest.sizes | Banner ad size/format details. |
| `bidfloor` | double | optional | AdRequest.bidFloor (default `0.0`) | Minimum bid price (CPM). Bids below this are rejected. |
| `bidfloorcur` | String | optional | hardcoded `"USD"` | Currency of the bid floor. |
| `secure` | Integer | optional | AdRequest.secure | `1` = the page is HTTPS, so the ad creative must also be HTTPS. |
| `rwdd` | Integer | optional | AdRequest.rewarded | `1` = rewarded ad (user gets in-app reward for watching). |
| `tagid` | String | optional | AdRequest.tagId | Publisher's internal name for this ad slot (e.g. `"homepage-leaderboard"`). |
| `instl` | Integer | optional | AdRequest.interstitial | `1` = interstitial (full-screen takeover ad). |

### Banner

Details about a display ad slot — size, allowed formats, restrictions.

| Field | Type | Spec Status | Source | Purpose |
|-------|------|-------------|--------|---------|
| `w` | Integer | optional | first AdRequest.sizes entry | Preferred width in pixels (e.g. `728`). |
| `h` | Integer | optional | first AdRequest.sizes entry | Preferred height in pixels (e.g. `90`). |
| `format` | List\<Format\> | **recommended** | all AdRequest.sizes entries | All acceptable size combinations. Always populated. |
| `btype` | List\<Integer\> | optional | AdRequest.btype | Blocked banner creative types (e.g. `1` = XHTML text, `4` = iframe). |
| `battr` | List\<Integer\> | optional | AdRequest.battr | Blocked creative attributes (e.g. `6` = auto-expand, `14` = audio auto-play). |
| `pos` | Integer | optional | AdRequest.pos | Ad position: `1` = above the fold, `3` = below. Above-fold commands higher prices. |

### Format

An acceptable width/height combination for a banner slot.

| Field | Type | Purpose |
|-------|------|---------|
| `w` | int | Width in pixels. |
| `h` | int | Height in pixels. |

### Site

Context about the webpage where the ad will appear. DSPs use this to decide if the context is brand-safe and relevant.

| Field | Type | Spec Status | Source | Purpose |
|-------|------|-------------|--------|---------|
| `id` | String | **recommended** | AdRequest.site.id | Publisher-assigned site ID. |
| `name` | String | optional | AdRequest.site.name | Human-readable site name. |
| `domain` | String | optional | AdRequest.site.domain | Top-level domain, e.g. `"example.com"`. |
| `page` | String | optional | AdRequest.site.page | Full URL of the page requesting the ad. |
| `ref` | String | optional | AdRequest.site.ref | Referrer URL — where the user came from. |
| `cat` | List\<String\> | optional | AdRequest.site.cat | IAB content categories (e.g. `"IAB9-30"` = Sci-Fi). Helps DSPs with contextual targeting. |
| `publisher` | Publisher | optional | server config | Info about the site owner. Always set from server-side publisher identity. |

### App

Context about a mobile or gaming app where the ad will appear. Analogous to `Site` but for non-web inventory. Per OpenRTB spec, a BidRequest contains either `Site` or `App`, never both.

| Field | Type | Spec Status | Source | Purpose |
|-------|------|-------------|--------|---------|
| `id` | String | **recommended** | AdRequest.app.id | App ID from our publisher registration. |
| `name` | String | optional | AdRequest.app.name | App name, e.g. `"Cool Game"`. |
| `bundle` | String | optional | AdRequest.app.bundle | Package name: `"com.coolgame.app"` (Android) or App Store ID (iOS). DSPs use this for app-level targeting and blocklists. |
| `storeurl` | String | optional | AdRequest.app.storeurl | App store URL (Google Play or Apple App Store). |
| `domain` | String | optional | AdRequest.app.domain | App developer's domain. |
| `cat` | List\<String\> | optional | AdRequest.app.cat | IAB content categories. Same taxonomy as Site. |
| `ver` | String | optional | AdRequest.app.ver | App version string. |
| `publisher` | Publisher | optional | server config | Reuses the same Publisher record as Site. Always set from server-side publisher identity. |

### Publisher

The entity that owns the site/app and is selling ad space. Set from server-side config (we're the exchange, we know our publisher).

| Field | Type | Purpose |
|-------|------|---------|
| `id` | String | Our internal publisher ID (e.g. `"adserve"`). |
| `name` | String | Publisher's business name (e.g. `"AdServe"`). |
| `domain` | String | Publisher's primary domain (e.g. `"adserve.io"`). |

### Device

The user's device. This is how DSPs do device targeting (mobile vs desktop), geo-targeting, and frequency capping. Assembled from three sources: HTTP headers (ua, ip), AdRequest.device (SDK-reported fields), and gRPC user-service (demographics fallbacks).

| Field | Type | Spec Status | Source | Purpose |
|-------|------|-------------|--------|---------|
| `ua` | String | optional | `User-Agent` HTTP header | User-Agent string from the browser. |
| `geo` | Geo | **recommended** | gRPC user-service demographics | Geographic location of the user. |
| `ip` | String | optional | `X-Forwarded-For` / remote addr | IPv4 address (typically truncated for privacy). |
| `devicetype` | Integer | optional | AdRequest.device.type | `1` = mobile/tablet, `2` = PC, `3` = connected TV, `7` = set-top box. |
| `make` | String | optional | AdRequest.device.make | Device manufacturer, e.g. `"Apple"`. |
| `model` | String | optional | AdRequest.device.model | Device model, e.g. `"iPhone"`. |
| `os` | String | optional | AdRequest.device.os, fallback gRPC | Operating system, e.g. `"iOS"`, `"Android"`. |
| `osv` | String | optional | AdRequest.device.osv | OS version, e.g. `"17.2"`. |
| `language` | String | optional | AdRequest.device.language, fallback gRPC | Browser language (ISO 639-1), e.g. `"en"`. |
| `js` | Integer | optional | AdRequest.device.js (default `1`) | `1` = JavaScript is supported. Almost always 1. |
| `w` | Integer | optional | AdRequest.device.w | Screen width in pixels. |
| `h` | Integer | optional | AdRequest.device.h | Screen height in pixels. |
| `dnt` | Integer | **recommended** | AdRequest.device.dnt (default `0`) | `1` = Do Not Track header is set. DSPs should limit tracking. Defaults to 0. |
| `lmt` | Integer | **recommended** | AdRequest.device.lmt (default `0`) | `1` = Limit Ad Tracking (iOS/Android setting). Defaults to 0. |

### Geo

Geographic location data, derived from GPS or IP address.

| Field | Type | Spec Status | Source | Purpose |
|-------|------|-------------|--------|---------|
| `lat` | Double | optional | AdRequest.device.geo.lat | Latitude (GPS). |
| `lon` | Double | optional | AdRequest.device.geo.lon | Longitude (GPS). |
| `country` | String | optional | gRPC user-service | ISO 3166-1 alpha-3 country code, e.g. `"USA"`, `"HUN"`. |
| `region` | String | optional | gRPC user-service | Region/state code, e.g. `"CA"` for California. |
| `city` | String | optional | AdRequest.device.geo.city | City name. |
| `type` | Integer | **recommended** | hardcoded `2` | How location was determined: `1` = GPS, `2` = IP-derived. GPS is more valuable. |
| `accuracy` | Integer | **recommended** | AdRequest.device.geo.accuracy | Estimated location accuracy in meters. |

### User

What we know about the user viewing the page. **More user data = higher bid prices** — this is why cookies and first-party data matter so much.

| Field | Type | Spec Status | Source | Purpose |
|-------|------|-------------|--------|---------|
| `id` | String | optional | gRPC user-service | Our first-party user ID. |
| `data` | List\<Data\> | optional | gRPC segment-service | Audience segments attached to this user. |
| `consent` | String | optional | AdRequest.consent | TCF 2.2 consent string — encodes which vendors the user consented to (GDPR). |
| `yob` | Integer | optional | AdRequest.yob | Year of birth (for age targeting). |
| `gender` | String | optional | AdRequest.gender | `"M"`, `"F"`, or `"O"`. |

### Data

A block of audience segment data from a specific provider (us, in this case).

| Field | Type | Purpose |
|-------|------|---------|
| `id` | String | Data provider ID, e.g. `"adserve"`. |
| `name` | String | Provider name, e.g. `"AdServe"`. |
| `segment` | List\<Segment\> | The actual segments this user belongs to. |

### Segment

A single audience segment — e.g. "sports enthusiast", "high spender", "age 25-34".

| Field | Type | Purpose |
|-------|------|---------|
| `id` | String | Segment ID from our segment service. |
| `name` | String | Human-readable segment name. |
| `value` | String | Segment score or value (e.g. confidence score). |

### Source

Metadata about the supply chain — who is selling this inventory and through which intermediaries.

| Field | Type | Spec Status | Source | Purpose |
|-------|------|-------------|--------|---------|
| `fd` | Integer | **recommended** | hardcoded `0` | Entity responsible for final sale: `0` = exchange (us), `1` = upstream source. |
| `schain` | Schain | **recommended** | server config | Supply chain object — proves the ad request is legitimate. |
| `tid` | String | **recommended** | server-generated trace ID | Transaction ID. Used for debugging across systems. |
| `pchain` | String | **recommended** | server config | TAG Payment ID chain string for payment authorization. |

### Schain (Supply Chain)

An [ads.txt/sellers.json](https://iabtechlab.com/ads-txt/) companion. It's a chain of all intermediaries handling this ad request — prevents fraud by proving every hop is authorized.

| Field | Type | Purpose |
|-------|------|---------|
| `complete` | int | `1` = this chain includes all nodes from publisher to DSP. `0` = partial. |
| `nodes` | List\<SchainNode\> | Ordered list of entities in the supply chain. |
| `ver` | String | Schain spec version, `"1.0"`. |

### SchainNode

One entity in the supply chain. Each intermediary that touches the ad request adds a node. DSPs verify these nodes against ads.txt/sellers.json to confirm authorization.

| Field | Type | Purpose |
|-------|------|---------|
| `asi` | String | Advertising System Identifier — the domain of this entity, e.g. `"adserve.io"`. Must match an entry in sellers.json. |
| `sid` | String | Seller ID — the publisher's account ID within this system. |
| `hp` | int | Handles Payment: `1` = this node handles payment to the publisher. |
| `rid` | String | Request ID — unique ID for this request within this node (for auditing). |

**Example**: When we send a bid request, our schain says: "This request comes from adserve.io (asi), the publisher is a direct seller (sid=direct), we handle payment (hp=1), and here's the request ID for audit trails (rid)."

### Regs (Regulations)

Privacy and legal compliance signals. **Getting this wrong has legal consequences** — GDPR fines up to 4% of global revenue, COPPA violations up to $50k per incident.

| Field | Type | Spec Status | Source | Purpose |
|-------|------|-------------|--------|---------|
| `coppa` | Integer | optional | AdRequest.regs.coppa | `1` = COPPA applies (Children's Online Privacy Protection Act). No behavioral targeting allowed for users under 13. |
| `gdpr` | Integer | optional | AdRequest.regs.gdpr | `1` = GDPR applies (EU/EEA users). Must have valid consent before processing personal data. |
| `usPrivacy` | String | optional | AdRequest.regs.usPrivacy | CCPA privacy string (California), format: `"1YNN"`. Serializes as `us_privacy` in JSON. |
| `gpp` | String | optional | AdRequest.regs.gpp | IAB Global Privacy Platform string — a unified consent format replacing the patchwork of regional strings. |

## Response-Side Records

### BidResponse

The top-level response from a demand partner. One response per auction.

| Field | Type | Purpose |
|-------|------|---------|
| `id` | String | Must match the `BidRequest.id` — this is how we correlate responses to auctions. |
| `seatbid` | List\<SeatBid\> | Bids grouped by "seat" (bidder identity). Usually one seat per response. |
| `cur` | String | Currency of the bid prices, e.g. `"USD"`. |
| `nbr` | Integer | No-bid reason code (if no `seatbid`). Common codes: `0` = unknown, `2` = blocked, `4` = missing markup. |

### SeatBid

A group of bids from one "seat" (bidder account). A DSP might operate multiple seats for different advertisers.

| Field | Type | Purpose |
|-------|------|---------|
| `bid` | List\<Bid\> | The actual bids. Each bid targets one impression from the request. |
| `seat` | String | Seat ID — identifies which advertiser/account within the DSP is bidding. |

### Bid

A single bid on a single impression. **This is the money** — the price field is what drives our revenue.

| Field | Type | Purpose |
|-------|------|---------|
| `id` | String | Unique bid ID. |
| `impid` | String | Which impression this bid is for — must match an `Imp.id` from the request. |
| `price` | double | Bid price in CPM (cost per thousand impressions). If this bid wins at $5.00 CPM, we earn ~$0.005 per impression shown. |
| `adm` | String | Ad markup — the actual HTML/JavaScript/VAST that renders the ad. |
| `adid` | String | Advertiser's ad ID (for creative review/approval). |
| `adomain` | List\<String\> | Advertiser domains, e.g. `["nike.com"]`. Used for brand safety checks. |
| `crid` | String | Creative ID — unique identifier for this specific creative asset. |
| `nurl` | String | Win notice URL — we call this URL to notify the DSP they won. Triggers billing. |
| `lurl` | String | Loss notice URL — we call this to notify the DSP they lost (optional, helps DSPs optimize). |
| `burl` | String | Billing notice URL — called when the impression is actually rendered (more accurate than nurl). |
| `w` | Integer | Creative width in pixels. |
| `h` | Integer | Creative height in pixels. |
| `dealId` | String | PMP (Private Marketplace) deal ID, if this bid is part of a pre-negotiated deal. Serializes as `deal_id` in JSON. |

## Non-OpenRTB Records

These records are used for internal communication with our ML service, not for partner bidding.

### MlPredictionRequest

Sent to our ML inference service to get CTR/CVR predictions.

| Field | Type | Purpose |
|-------|------|---------|
| `userId` | String | User ID for personalization. |
| `traceId` | String | Trace ID for request correlation. |
| `segments` | List\<String\> | User's audience segment names — the ML model uses these as features. |

### MlPredictionResponse

Prediction results from the ML service. Used to score and rank bid opportunities.

| Field | Type | Purpose |
|-------|------|---------|
| `ctr` | double | Predicted Click-Through Rate (e.g. `0.03` = 3% chance user clicks). |
| `cvr` | double | Predicted Conversion Rate (e.g. `0.008` = 0.8% chance of purchase after click). |
| `modelVersion` | String | Which model version produced this prediction (for A/B testing models). |
| `traceId` | String | Echo of the trace ID for correlation. |

## Auction Notifications

After the auction completes, the exchange notifies each bidder of the outcome. OpenRTB defines three notification URLs:

| URL | When Fired | Direction |
|-----|-----------|-----------|
| `nurl` (win notice) | Immediately after auction — winner only | Exchange → winning DSP |
| `lurl` (loss notice) | Immediately after auction — each loser | Exchange → losing DSP |
| `burl` (billing notice) | When the ad is actually rendered on the user's device | Client-side → DSP |

**Current implementation**: `nurl` and `lurl` are fired asynchronously via `NotificationService` after each auction. `burl` is not yet implemented — it requires client-side integration (JavaScript/SDK) to detect when the ad creative actually renders.

Notifications are **fire-and-forget**: they use short timeouts (500ms connect, 2s total) and do not retry on failure. This is standard practice — notifications are best-effort, and DSPs are built to tolerate some loss.

## Substitution Macros

Notification URLs contain macro placeholders that the exchange replaces with actual auction values before calling. Handled by `AuctionMacros`.

| Macro | Replaced With | Used In |
|-------|--------------|---------|
| `${AUCTION_ID}` | The auction/request ID | nurl, lurl, burl |
| `${AUCTION_PRICE}` | Winning price (win) or bid price (loss) | nurl, lurl, burl |
| `${AUCTION_BID_ID}` | The bid ID from the DSP's response | nurl, burl |
| `${AUCTION_CURRENCY}` | Currency code, e.g. `"USD"` | nurl, burl |
| `${AUCTION_LOSS}` | Loss reason code (integer) | lurl |

**Example**: A DSP returns `nurl = "https://dsp.com/win?auction=${AUCTION_ID}&price=${AUCTION_PRICE}"`. After the auction, we replace the macros and call `https://dsp.com/win?auction=abc-123&price=2.75`.

## Bid Validation

Before a bid enters the auction, `BidValidator` checks:

1. **Impression match** — `bid.impid` must match an `Imp.id` from the request. Rejects bids targeting non-existent ad slots.
2. **Bid floor** — `bid.price` must be ≥ the `Imp.bidfloor` for the matched impression. Rejects below-floor bids.
3. **Blocked advertisers** — `bid.adomain` must not contain any domain in `BidRequest.badv`. Rejects ads from blocked advertisers.

Invalid bids are logged and counted via the `ad_auction_invalid_bids` metric.

## No-Bid Reason Codes

When a DSP declines to bid, it can return a `BidResponse` with an empty `seatbid` and an `nbr` (no-bid reason) code. Common codes:

| Code | Meaning |
|------|---------|
| 0 | Unknown error |
| 1 | Technical error |
| 2 | Invalid request |
| 3 | Known web spider |
| 4 | Suspected non-human traffic |
| 5 | Cloud/data center/proxy IP |
| 6 | Unsupported device |
| 7 | Blocked publisher or site |
| 8 | Unmatched user |
| 10 | Missing/ineligible creative |

These are tracked via the `ad_auction_nobids` metric with partner and reason tags.

### OpenRTB Loss Reason Codes

Sent in `lurl` via the `${AUCTION_LOSS}` macro:

| Code | Meaning |
|------|---------|
| 1 | Internal error |
| 2 | Impression opportunity expired |
| 3 | Invalid bid response |
| 102 | Lost to higher bid (most common) |
