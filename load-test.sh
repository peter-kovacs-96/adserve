#!/bin/bash
# AdServe Load Test Script
# Requires: sudo apt install hey

URL="http://localhost:8080/api/v1/ads/request"
RATE=${1:-200}       # requests per second
DURATION=${2:-5m}     # duration
CONCURRENCY=${3:-70} # concurrent workers
WARMUP=${4:-100}      # warm-up requests

REQUEST_BODY='{
  "userId": "user-123",
  "tagId": "tag-homepage-leaderboard",
  "sizes": [{"w": 728, "h": 90}, {"w": 970, "h": 250}],
  "bidFloor": 0.5,
  "secure": true,
  "interstitial": false,
  "rewarded": false,
  "pos": 1,
  "btype": [4],
  "battr": [6, 14],
  "site": {
    "id": "site-001",
    "name": "Example News",
    "domain": "example.com",
    "page": "https://example.com/article/123",
    "ref": "https://google.com/search?q=example",
    "cat": ["IAB12", "IAB12-2"]
  },
  "device": {
    "type": 2,
    "make": "Apple",
    "model": "Macintosh",
    "os": "macOS",
    "osv": "15.3",
    "language": "en",
    "w": 1920,
    "h": 1080,
    "dnt": 0,
    "lmt": 0,
    "js": 1,
    "ifa": "6d92078a-8246-4ba4-ae5b-76104861e7dc",
    "geo": {
      "lat": 37.7749,
      "lon": -122.4194,
      "city": "San Francisco",
      "accuracy": 50
    }
  },
  "consent": "CPXxRfAPXxRfAAfKABENB-CgAAAAAAAAAAYgAAAAAAAA",
  "yob": 1990,
  "gender": "M",
  "regs": {
    "gdpr": 0,
    "coppa": 0,
    "usPrivacy": "1YNN",
    "gpp": ""
  },
  "tmax": 100,
  "blockedCategories": ["IAB25", "IAB26"],
  "blockedAdvertisers": ["blocked-example.com"],
  "test": true
}'

echo "=== AdServe Load Test ==="
echo "  URL: $URL"
echo "  Rate: $RATE req/sec"
echo "  Duration: $DURATION"
echo "  Concurrency: $CONCURRENCY"
echo ""

# Warm-up phase to open circuit breakers
if [ "$WARMUP" -gt 0 ]; then
    echo "Warm-up: sending $WARMUP requests to stabilize circuit breakers..."
    hey -n "$WARMUP" -c "$CONCURRENCY" -m POST \
      -H "Content-Type: application/json" \
      -d "$REQUEST_BODY" \
      "$URL" > /dev/null 2>&1
    echo "Warm-up complete. Starting measured test..."
    echo ""
fi

echo "Press Ctrl+C to stop"
echo ""

hey -z "$DURATION" -q "$RATE" -c "$CONCURRENCY" -m POST \
  -H "Content-Type: application/json" \
  -d "$REQUEST_BODY" \
  "$URL"

# Find slow requests in logs (>150ms)
echo ""
echo "=== Slow Requests (>150ms) from logs ==="
docker logs ad-orchestration 2>&1 | grep "Ad request completed" | \
  sed -n 's/.*requestId=\([^ ]*\).*totalMs=\([0-9]*\).*/\2ms requestId=\1/p' | \
  awk '$1+0 > 150' | sort -rn | head -20
