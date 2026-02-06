package io.adserve.orchestration.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AdMetrics {

    private final MeterRegistry meterRegistry;

    public AdMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAuctionWin(String partnerId) {
        Counter.builder("ad_auction_wins")
                .description("Number of auction wins per partner")
                .tag("partner", partnerId)
                .register(meterRegistry)
                .increment();
    }
}
