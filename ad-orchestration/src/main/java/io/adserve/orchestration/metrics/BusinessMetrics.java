package io.adserve.orchestration.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Business metrics for ad serving KPIs.
 * <p>
 * Enable/disable via: METRICS_BUSINESS_ENABLED=true/false
 * <p>
 * Metrics:
 * - ad_requests_total: Total ad requests received
 * - ad_requests_success: Successful ad requests
 * - ad_requests_error: Failed ad requests
 * - ad_request_duration: Overall request duration
 * - ad_auction_wins: Auction wins per partner
 */
@Component
public class BusinessMetrics {

    @Getter
    private final boolean enabled;
    private final MeterRegistry meterRegistry;

    private final Counter requestsTotal;
    private final Counter requestsSuccess;
    private final Counter requestsError;
    private final Timer requestDuration;

    public BusinessMetrics(MetricsProperties properties, MeterRegistry meterRegistry) {
        this.enabled = properties.businessEnabled();
        this.meterRegistry = meterRegistry;

        if (enabled) {
            this.requestsTotal = Counter.builder("ad_requests_total")
                    .description("Total number of ad requests")
                    .register(meterRegistry);

            this.requestsSuccess = Counter.builder("ad_requests_success")
                    .description("Number of successful ad requests")
                    .register(meterRegistry);

            this.requestsError = Counter.builder("ad_requests_error")
                    .description("Number of failed ad requests")
                    .register(meterRegistry);

            this.requestDuration = Timer.builder("ad_request_duration")
                    .description("Ad request duration")
                    .register(meterRegistry);
        } else {
            this.requestsTotal = null;
            this.requestsSuccess = null;
            this.requestsError = null;
            this.requestDuration = null;
        }
    }

    public void incrementRequestsTotal() {
        if (enabled) {
            requestsTotal.increment();
        }
    }

    public void incrementRequestsSuccess() {
        if (enabled) {
            requestsSuccess.increment();
        }
    }

    public void incrementRequestsError() {
        if (enabled) {
            requestsError.increment();
        }
    }

    public void recordRequestDuration(Duration duration) {
        if (enabled) {
            requestDuration.record(duration);
        }
    }

    public void recordAuctionWin(String partnerId) {
        if (enabled) {
            Counter.builder("ad_auction_wins")
                    .description("Number of auction wins per partner")
                    .tag("partner", partnerId)
                    .register(meterRegistry)
                    .increment();
        }
    }
}
