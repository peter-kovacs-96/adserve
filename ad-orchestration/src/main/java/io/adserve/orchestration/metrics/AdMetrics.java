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
}
