#!/bin/bash
# AdServe Load Test Script
# Requires: sudo apt install hey

URL="http://localhost:8080/api/v1/ads/request"
RATE=${1:-100}        # requests per second (default: 100)
DURATION=${2:-5m}     # duration (default: 5 minutes)
CONCURRENCY=${3:-10}  # concurrent workers (default: 10)
WARMUP=${4:-20}       # warm-up requests (default: 20)

REQUEST_BODY='{"userId":"user-123","deviceType":"mobile","country":"USA"}'

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
