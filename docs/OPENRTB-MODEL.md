# OpenRTB 2.6 Model Reference

This document explains every record in `io.adserve.orchestration.openrtb` — what it represents, why it exists in the OpenRTB protocol, and which fields matter most.

## How OpenRTB Works

OpenRTB (Open Real-Time Bidding) is the industry-standard protocol for programmatic advertising. When a user visits a webpage with an ad slot:

1. **Publisher's ad server** sends a `BidRequest` to one or more demand partners (DSPs)
2. Each DSP evaluates the request and returns a `BidResponse` with a price
3. The highest bid wins the auction and their ad is shown

We (AdServe) sit in the middle as an SSP (Supply-Side Platform) — we build the `BidRequest`, fan it out to partners, collect `BidResponse`s, and pick the winner.

## Request-Side Records

### BidRequest

The top-level auction object. One `BidRequest` = one auction opportunity.

| Field | Type | Purpose |
|-------|------|---------|
| `id` | String | Unique auction ID. Partners echo this back in their response so we can match it. |
| `imp` | List\<Imp\> | The ad slots available in this auction. Usually one, but a page can offer multiple. |
| `site` | Site | Context about the webpage where the ad will appear. |
| `device` | Device | The user's device — browser, OS, screen size, location. |
| `user` | User | What we know about the user — ID, segments, consent status. |
| `source` | Source | Supply chain info — proves we're a legitimate seller. |
| `regs` | Regs | Privacy/regulation signals — GDPR, COPPA, CCPA. |
| `at` | Integer | Auction type. `1` = first-price (winner pays what they bid). `2` = second-price. Industry moved to first-price. |
| `tmax` | Integer | Maximum time in ms we'll wait for a response. Partners that respond slower get ignored. |
| `cur` | List\<String\> | Accepted currencies, e.g. `["USD"]`. |
| `bcat` | List\<String\> | Blocked advertiser categories (IAB taxonomy). Publisher says "no gambling ads". |
| `badv` | List\<String\> | Blocked advertiser domains. Publisher says "no ads from competitor.com". |

### Imp (Impression)

An individual ad placement within the auction. "Impression" = one ad shown to one user.

| Field | Type | Purpose |
|-------|------|---------|
| `id` | String | Impression ID within this auction (e.g. `"imp-1"`). |
| `banner` | Banner | If this slot accepts display (banner) ads, the size/format details. |
| `bidfloor` | double | Minimum bid price (CPM). Bids below this are rejected. `0` = no floor. |
| `bidfloorcur` | String | Currency of the bid floor, typically `"USD"`. |
| `secure` | Integer | `1` = the page is HTTPS, so the ad creative must also be HTTPS. |
| `rwdd` | Integer | `1` = rewarded ad (user gets in-app reward for watching). Common in mobile games. |
| `tagid` | String | Publisher's internal name for this ad slot (e.g. `"homepage-leaderboard"`). |
| `instl` | Integer | `1` = interstitial (full-screen takeover ad). |

### Banner

Details about a display ad slot — size, allowed formats, restrictions.

| Field | Type | Purpose |
|-------|------|---------|
| `w` | Integer | Preferred width in pixels (e.g. `728`). |
| `h` | Integer | Preferred height in pixels (e.g. `90`). |
| `format` | List\<Format\> | Alternative sizes the slot can accept (e.g. 728x90 or 970x250). |
| `btype` | List\<Integer\> | Blocked banner creative types (e.g. `1` = XHTML text, `4` = iframe). |
| `battr` | List\<Integer\> | Blocked creative attributes (e.g. `6` = auto-expand, `14` = audio auto-play). |
| `pos` | Integer | Ad position: `1` = above the fold (visible without scrolling), `3` = below. Above-fold commands higher prices. |

### Format

An acceptable width/height combination for a banner slot.

| Field | Type | Purpose |
|-------|------|---------|
| `w` | int | Width in pixels. |
| `h` | int | Height in pixels. |

### Site

Context about the webpage where the ad will appear. DSPs use this to decide if the context is brand-safe and relevant.

| Field | Type | Purpose |
|-------|------|---------|
| `id` | String | Publisher-assigned site ID. |
| `name` | String | Human-readable site name. |
| `domain` | String | Top-level domain, e.g. `"coolgame.io"`. |
| `page` | String | Full URL of the page requesting the ad. |
| `ref` | String | Referrer URL — where the user came from. |
| `cat` | List\<String\> | IAB content categories (e.g. `"IAB9-30"` = Sci-Fi). Helps DSPs with contextual targeting. |
| `publisher` | Publisher | Info about the site owner. |

### Publisher

The entity that owns the site and is selling ad space.

| Field | Type | Purpose |
|-------|------|---------|
| `id` | String | Our internal publisher ID (e.g. `"pub-12345"`). |
| `name` | String | Publisher's business name. |
| `domain` | String | Publisher's primary domain. |

### Device

The user's device. This is how DSPs do device targeting (mobile vs desktop), geo-targeting, and frequency capping.

| Field | Type | Purpose |
|-------|------|---------|
| `ua` | String | User-Agent string from the browser. |
| `geo` | Geo | Geographic location of the user. |
| `ip` | String | IPv4 address (typically truncated for privacy, e.g. `"203.0.113.0"`). |
| `devicetype` | Integer | `1` = mobile/tablet, `2` = PC, `3` = connected TV, `7` = set-top box. |
| `make` | String | Device manufacturer, e.g. `"Apple"`. |
| `model` | String | Device model, e.g. `"iPhone"`. |
| `os` | String | Operating system, e.g. `"iOS"`, `"Android"`. |
| `osv` | String | OS version, e.g. `"17.2"`. |
| `language` | String | Browser language (ISO 639-1), e.g. `"en"`. |
| `js` | Integer | `1` = JavaScript is supported. Almost always 1. |
| `w` | Integer | Screen width in pixels. |
| `h` | Integer | Screen height in pixels. |
| `dnt` | Integer | `1` = Do Not Track header is set. DSPs should limit tracking. |
| `lmt` | Integer | `1` = Limit Ad Tracking (iOS/Android setting). Similar to DNT but OS-level. |

### Geo

Geographic location data, derived from GPS or IP address.

| Field | Type | Purpose |
|-------|------|---------|
| `lat` | Double | Latitude (GPS). |
| `lon` | Double | Longitude (GPS). |
| `country` | String | ISO 3166-1 alpha-3 country code, e.g. `"USA"`, `"HUN"`. |
| `region` | String | Region/state code, e.g. `"CA"` for California. |
| `city` | String | City name. |
| `type` | Integer | How location was determined: `1` = GPS, `2` = IP-derived. GPS is more valuable. |

### User

What we know about the user viewing the page. **More user data = higher bid prices** — this is why cookies and first-party data matter so much.

| Field | Type | Purpose |
|-------|------|---------|
| `id` | String | Our first-party user ID. |
| `data` | List\<Data\> | Audience segments attached to this user (from our segment service). |
| `consent` | String | TCF 2.2 consent string — encodes which vendors the user consented to (GDPR). |
| `yob` | Integer | Year of birth (for age targeting, if known). |
| `gender` | String | `"M"`, `"F"`, or `"O"`. |

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

| Field | Type | Purpose |
|-------|------|---------|
| `schain` | Schain | Supply chain object — proves the ad request is legitimate. |
| `tid` | String | Transaction ID (our trace ID). Used for debugging across systems. |

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

| Field | Type | Purpose |
|-------|------|---------|
| `coppa` | Integer | `1` = COPPA applies (Children's Online Privacy Protection Act). No behavioral targeting allowed for users under 13. |
| `gdpr` | Integer | `1` = GDPR applies (EU/EEA users). Must have valid consent before processing personal data. |
| `usPrivacy` | String | CCPA privacy string (California), format: `"1YNN"` — version, opted-out, LSPA-covered, —. Serializes as `us_privacy` in JSON. |
| `gpp` | String | IAB Global Privacy Platform string — a unified consent format replacing the patchwork of regional strings. |

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
