# AdServe Business Plan — From PoC to Revenue

## Executive Summary

AdServe is a high-performance SSP (Supply-Side Platform) that connects publishers (websites, games, apps)
with advertisers (DSPs/demand partners) via real-time OpenRTB 2.6 auctions. Revenue comes from a take rate
on every impression sold through the platform.

Current state: working PoC with auction engine at 300K+ RPS, sub-100ms latency, ML-powered bid optimization,
and user/segment/targeting enrichment. Missing: publisher-facing SDK, real DSP integrations, billing, and
compliance infrastructure.

## Revenue Model

### How SSPs Make Money

SSPs earn a percentage of every winning bid before paying the publisher.

| Metric                        | Industry Average          | Our Target            |
|-------------------------------|---------------------------|-----------------------|
| SSP take rate                 | 10-20% (median ~14%)     | 10-12% (undercut)     |
| Display CPM                   | $2-$10                   | $3-$8                 |
| Video CPM                     | $15-$30                  | Phase 2               |
| Rewarded video CPM (gaming)   | $10-$25                  | Phase 2               |
| Average programmatic CPM      | $5.82 (2024 industry)    | Dependent on vertical |

### Revenue Projections

| Stage   | Publishers       | Impressions/month | Gross Revenue | Our Cut (12%) | Timeline    |
|---------|------------------|-------------------|---------------|---------------|-------------|
| Seed    | 10-50            | 10M               | ~$58K         | ~$7K          | Month 1-6   |
| Early   | 500              | 500M              | ~$2.9M        | ~$350K        | Month 6-12  |
| Growth  | 5,000            | 10B               | ~$58M         | ~$7M          | Year 2      |
| Scale   | 50,000+          | 100B+             | ~$580M+       | ~$70M+        | Year 3+     |

Note: 90% of digital ads worldwide are now sold programmatically (~$700B global spend by 2026).
Even capturing 0.01% of global spend = $70M gross.

### Pricing Models We Support

| Model | Description                              | Who Pays        |
|-------|------------------------------------------|-----------------|
| CPM   | Cost per 1,000 impressions               | Advertiser pays |
| CPC   | Cost per click                           | Advertiser pays |
| CPA   | Cost per action (install, purchase, etc.) | Advertiser pays |

In all models, our revenue is a percentage of the transaction.

## The Chicken-and-Egg Problem

The core marketplace challenge: publishers want demand (advertisers), advertisers want supply (publishers).

### Strategy: Supply First, Niche Down

**1. Pick ONE vertical: indie gaming**

Why gaming:
- Indie game devs are underserved by Google/Unity duopoly
- They hate 30-50% take rates of big platforms
- Rewarded video ads have 85%+ completion rates (high value)
- Gaming ad market is $100B+ and growing
- Our sub-100ms latency matters for game UX
- Devs are technical, will appreciate clean SDK and docs

**2. Subsidize early publishers**

- Guarantee a minimum CPM floor for first 90 days (fund from seed capital)
- Backfill unsold inventory from established ad networks via OpenRTB integrations
- Zero-cost onboarding, no minimum traffic requirements

**3. Connect to existing DSPs for demand**

- The Trade Desk, DV360, Amazon DSP, Xandr all accept new SSP partners via OpenRTB 2.6
- They don't care who we are — they care about inventory quality and fraud rates
- `ads.txt` + `sellers.json` + SupplyChain (`schain`) compliance = trust signal
- This gives publishers immediate fill rates without us having to recruit advertisers directly

**4. Land anchor partners**

- One recognizable game studio using our SDK = social proof
- One recognizable brand advertising through us = publisher magnet

**5. Expand vertically**

- Once gaming proves the model: web publishers, mobile apps, CTV
- Each vertical reuses the same auction engine and DSP integrations

## Competitive Positioning

### Don't compete with Google head-on

| Incumbent         | Their Weakness                      | Our Angle                                   |
|-------------------|-------------------------------------|---------------------------------------------|
| Google AdSense/AdX| 32% take rate, opaque pricing       | Transparent 10-12%, publish `sellers.json`   |
| Unity Ads         | Gaming-only, closed ecosystem       | Open protocol (OpenRTB), cross-platform      |
| ironSource/AppLovin| Mobile-only, heavy SDK             | Lightweight SDK, web + mobile + game engines |
| Amazon Publisher  | Locked to Amazon DSP                | Open to all DSPs                             |

### Our Differentiators

1. **Performance** — sub-100ms auction at 300K+ RPS (most SSPs: 200-500ms)
2. **Transparency** — published take rate, `sellers.json`, full `schain` in every bid request
3. **Developer experience** — simple SDK, clean docs, instant setup, no minimum traffic
4. **ML-powered optimization** — real-time CTR/CVR prediction increases publisher revenue
5. **Modern stack** — Java 25, virtual threads, structured concurrency (operational advantage, not marketing)

## What We Have vs What We Need

### Current State (PoC)

| Component                      | Status  | Detail                                              |
|--------------------------------|---------|-----------------------------------------------------|
| Auction engine                 | Done    | 300K RPS, first-price auction, StructuredTaskScope   |
| OpenRTB 2.6 bid response      | Partial | `Map<String, Object>`, needs typed records           |
| User enrichment                | Done    | gRPC user-service with demographics, device info     |
| Segment targeting              | Done    | gRPC segment-service with behavioral/interest data   |
| Targeting rules                | Done    | gRPC targeting-service with rule matching            |
| ML predictions (CTR/CVR)       | Done    | HTTP ml-inference service                            |
| Partner bidding (simulated)    | Done    | partner-simulator with 10 mock demand partners       |
| Monitoring                     | Done    | Prometheus + Grafana, separate management ports      |
| Health probes                  | Done    | `/livez`, `/readyz` on all services                  |

### Required for Business (Build Order)

#### Phase 1 — MVP (First Dollar)

| # | Component                   | Purpose                                          | Effort |
|---|-----------------------------|--------------------------------------------------|--------|
| 1 | Publisher JS SDK / ad tag   | Publishers drop `<script>` tag, ads appear       | Medium |
| 2 | Impression & click tracking | Count events, bill correctly                     | Medium |
| 3 | Full OpenRTB 2.6 compliance | Typed Java records, proper bid requests to DSPs  | Medium |
| 4 | Publisher registration      | Sign up, get API key (Spring Security 7)         | Small  |
| 5 | ads.txt verification        | Crawl publisher domains, verify authorization    | Small  |
| 6 | Basic reporting API         | Publishers see CPM, fill rate, revenue           | Small  |
| 7 | Publisher dashboard (web)   | Self-serve UI for publishers                     | Medium |

#### Phase 2 — Growth (Scale Revenue)

| # | Component                    | Purpose                                                    |
|---|------------------------------|------------------------------------------------------------|
| 1 | Prebid.js adapter            | Publishers using header bidding can add us as an SSP source |
| 2 | VAST video ad support        | Video ads = 3-5x higher CPMs than display                  |
| 3 | Multiple ad formats          | Banner, interstitial, native, rewarded video               |
| 4 | Fraud detection (IVT)        | Invalid Traffic filtering — advertisers demand it          |
| 5 | Publisher payment system      | Actually pay publishers (NET 30/60 via Stripe/PayPal)      |
| 6 | Real DSP integrations        | Connect to The Trade Desk, DV360, Amazon DSP, Xandr        |
| 7 | Mobile SDK (Android/iOS)     | Expand beyond web                                          |

#### Phase 3 — Moat (Defensibility)

| # | Component                     | Purpose                                                  |
|---|-------------------------------|----------------------------------------------------------|
| 1 | ML bid floor optimization     | Use ml-inference to maximize publisher revenue per impression |
| 2 | First-party data segments     | Segment-service becomes a real targeting differentiator   |
| 3 | Private marketplace (PMP)     | Direct deals between premium publishers and advertisers  |
| 4 | Self-serve advertiser portal  | Cut out DSP middlemen for direct campaigns               |
| 5 | CTV / OTT support             | Connected TV ads — highest CPMs in the market            |
| 6 | Audience extension            | Let advertisers find similar users across publisher base |

## Publisher SDK — How It Works

### Web (JavaScript ad tag)

What a publisher integrates:

```html
<!-- 1. Load SDK (async, non-blocking) -->
<script async src="https://cdn.adserve.io/sdk/v1/adserve.js?pub=pub-12345"></script>

<!-- 2. Define ad slot -->
<div id="ad-top-banner"
     data-ad-size="728x90"
     data-ad-slot="slot-001">
</div>

<!-- 3. Request ad -->
<script>
  (adserve = window.adserve || []).push({});
</script>
```

What the SDK does internally:
1. Collects context: page URL, referrer, viewport size, device info
2. POSTs to `POST /api/v1/ads/request` with publisher API key + context
3. Receives winning creative (`adm` field from OpenRTB bid response)
4. Creates a sandboxed iframe, renders the creative inside it
5. Fires impression beacon when ad is 50% visible for 1+ second (IAB viewability standard)
6. Tracks clicks (redirect through our click tracker or beacon-based)

### Mobile (native SDK)

Same flow but platform-specific:
- Android: AAR library, `AdView` widget
- iOS: Swift package, `AdBannerView`
- Unity: C# plugin, `AdServe.ShowRewardedAd()`

### Game Engine (rewarded ads)

```csharp
// Unity C# example
AdServe.Initialize("pub-12345");

// Show rewarded video ad
AdServe.ShowRewardedAd(onComplete: () => {
    // Give player their reward (coins, lives, etc.)
    player.AddCoins(100);
});
```

## Industry Standards & Compliance

### Required Protocols

| Standard       | Purpose                                               | Priority  |
|----------------|-------------------------------------------------------|-----------|
| OpenRTB 2.6    | Bid request/response format between SSP and DSPs      | Must have |
| ads.txt        | Publisher lists authorized sellers on their domain     | Must have |
| sellers.json   | We publish list of publishers we represent             | Must have |
| SupplyChain    | `schain` object in bid request traces intermediaries   | Must have |
| VAST 4.2       | Video ad serving template                             | Phase 2   |
| MRAID 3.0      | Mobile rich media ad interface                        | Phase 2   |
| Open Measurement| Viewability and verification SDK                      | Phase 2   |

### Required Legal/Privacy Compliance

| Regulation   | Requirement                                                 | Impact                  |
|-------------|-------------------------------------------------------------|-------------------------|
| GDPR        | Consent before processing EU user data for ad targeting     | Consent Management Platform (CMP) integration |
| CCPA/CPRA   | California opt-out of data sale                             | Honor `us_privacy` string in bid requests     |
| TCF 2.2     | IAB Transparency & Consent Framework                        | Pass consent signals in OpenRTB               |
| COPPA       | No behavioral targeting on children's content               | Age-gate and content classification           |
| ePrivacy    | Cookie consent (EU)                                         | First-party data strategy                     |

## Financial Model

### Cost Structure

| Category               | Monthly Cost (at scale) | Notes                                        |
|------------------------|------------------------|----------------------------------------------|
| Cloud infrastructure   | $50K-$200K             | Compute, bandwidth (egress is the killer)    |
| DSP integration fees   | Varies                 | Some DSPs charge per QPS                     |
| Fraud detection        | $10K-$50K              | IVT vendors (DoubleVerify, IAS, MOAT)        |
| Payment processing     | 2-3% of payouts        | Stripe/PayPal fees on publisher payments      |
| Personnel              | $200K-$500K            | Engineering, ad ops, sales                   |
| Legal/compliance       | $20K-$50K              | Privacy counsel, IAB membership              |

### Unit Economics

At growth stage (10B impressions/month):
- Gross revenue per impression: ~$0.00582 (at $5.82 CPM)
- Our take (12%): ~$0.000698 per impression
- Infrastructure cost per impression: ~$0.00001-$0.00005
- **Gross margin: ~95%+** (ad tech is high-margin at scale)

### Funding Requirements

| Stage        | Capital Needed | Purpose                                         |
|-------------|---------------|--------------------------------------------------|
| Pre-seed    | $0 (bootstrap) | PoC is built, validate with 5-10 publishers     |
| Seed        | $500K-$1M      | SDK, DSP integrations, first hires, publisher subsidies |
| Series A    | $5M-$10M       | Scale publisher acquisition, video ads, mobile SDK |

## User Data, Cookies & the Privacy Shift

### Why User Info = Money

Ad tech pricing is directly tied to how much you know about the user seeing the ad:

| Targeting Level        | What You Know                              | Typical CPM   |
|------------------------|--------------------------------------------|---------------|
| No targeting (blind)   | Nothing — just "a human visited a page"    | $0.50-$1      |
| Contextual             | Page content (sports site, cooking blog)   | $2-$4         |
| Demographic            | Age, gender, location                      | $4-$8         |
| Behavioral             | "This user shops for sneakers"             | $8-$15        |
| Retargeting            | "This user visited nike.com yesterday"     | $15-$50+      |

The more you know about the user, the more advertisers pay. A sneaker ad shown to someone who
browsed Nike shoes last week is worth 10-50x more than the same ad shown to a random person.

### How Third-Party Cookies Worked

Cookies were the primary mechanism for tracking users across websites:

1. User visits site-A.com -> our ad tag loads -> we set cookie: `adserve_id=abc123`
2. User visits site-B.com -> our ad tag loads -> we READ cookie: `adserve_id=abc123`
3. Now we know: "abc123 likes sports (site-A) AND tech (site-B)"
4. Advertiser pays 5x more because we can target precisely

These are third-party cookies — set by our domain (`adserve.io`) but read across many publisher domains.

### Third-Party Cookies Are Dying

| Browser | Status                                        |
|---------|-----------------------------------------------|
| Safari  | Blocked since 2020 (ITP)                      |
| Firefox | Blocked since 2022 (ETP)                      |
| Chrome  | Deprecating / restricted (Privacy Sandbox)    |

This is the biggest disruption in ad tech. The entire $700B industry built on third-party cookies
is being forced to change.

### What Replaces Them

| Alternative                          | How It Works                                                              | Who Pushes It     |
|--------------------------------------|---------------------------------------------------------------------------|-------------------|
| First-party data                     | Publisher collects data directly (login, preferences) — SSP enriches it   | Industry consensus|
| Google Privacy Sandbox / Topics API  | Browser categorizes user interests locally, shares topics (not identity)  | Google            |
| Unified ID 2.0                       | Hashed email-based identity, user opts in                                 | The Trade Desk    |
| Contextual targeting                 | Target based on page content, not user identity                           | Everyone (fallback)|
| Server-side ID resolution            | Match users via deterministic data (email, phone) server-side             | LiveRamp, ID5     |

### Why Our Architecture Is Well-Positioned

We don't depend on third-party cookies. Our enrichment happens server-side from the publisher's
first-party data. The flow:

```
Publisher's logged-in user visits page
  -> SDK sends: { publisherUserId: "xyz", pageUrl: "/sports/shoes" }
  -> We enrich via user-service + segment-service (publisher's OWN data)
  -> We send enriched bid request to DSPs
  -> DSPs bid higher because of targeting signals
  -> Publisher earns more, we earn more
```

What we already have that maps to this:
- **user-service** — first-party user data (demographics, device info)
- **segment-service** — behavioral segments (interest, demographic, contextual)
- **targeting-service** — rule-based targeting with eligible partner matching
- **ml-inference** — CTR/CVR prediction from available signals (no cookie dependency)

The industry is moving toward exactly this model — first-party data, server-side enrichment,
privacy-safe. Companies that relied on third-party cookies are scrambling. We would be starting
fresh with the right architecture from day one.

## Key Risks

| Risk                          | Mitigation                                              |
|-------------------------------|--------------------------------------------------------|
| No publisher adoption         | Start niche (gaming), subsidize CPM floors              |
| DSPs reject our inventory     | ads.txt compliance, IVT filtering, start with Prebid.js |
| Google/Unity change terms     | Open protocol (OpenRTB), no platform lock-in            |
| Privacy regulation tightens   | First-party data strategy, contextual targeting          |
| Ad fraud damages reputation   | Invest in IVT detection early (Phase 2)                  |
| Cash flow (NET 30/60 payouts) | DSPs pay us NET 60, we pay publishers NET 90 initially   |

## Immediate Next Steps

1. **Build the JS SDK** — this is the product surface publishers touch
2. **Add impression/click tracking** — without this, no billing
3. **Implement full OpenRTB 2.6** — typed records, proper bid requests out to DSPs
4. **Publisher registration + API keys** — Spring Security 7 with API key auth
5. **Deploy ads.txt verification** — crawl publisher domains on registration
6. **Connect to one real DSP** — The Trade Desk or Google Authorized Buyers via OpenRTB
7. **Find 5 indie game devs** — offer free integration, guaranteed CPM floor

## References

- OpenRTB 2.6 Specification: https://github.com/InteractiveAdvertisingBureau/openrtb2.x/blob/main/2.6.md
- IAB Tech Lab Standards: https://iabtechlab.com/standards/
- ads.txt Specification: https://iabtechlab.com/ads-txt/
- sellers.json Specification: https://iabtechlab.com/sellers-json/
- Prebid.js: https://prebid.org/
- VAST 4.2: https://iabtechlab.com/standards/vast/
- MRAID 3.0: https://iabtechlab.com/standards/mraid/
- TCF 2.2: https://iabeurope.eu/transparency-consent-framework/
