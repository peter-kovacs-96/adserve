package io.adserve.orchestration.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AdMetrics {

    private final MeterRegistry meterRegistry;

    public AdMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAuctionWin(String partnerId) {
        meterRegistry.counter("ad_auction_wins", "partner", partnerId).increment();
    }

    public void recordNoBid(String partnerId, int reasonCode) {
        meterRegistry.counter("ad_auction_nobids", "partner", partnerId,
                "reason", String.valueOf(reasonCode)).increment();
    }

    public void recordInvalidBid(String partnerId) {
        meterRegistry.counter("ad_auction_invalid_bids", "partner", partnerId).increment();
    }

    public void recordNotification(String type, boolean success) {
        meterRegistry.counter("ad_notifications", "type", type,
                "status", success ? "ok" : "failed").increment();
    }
}
