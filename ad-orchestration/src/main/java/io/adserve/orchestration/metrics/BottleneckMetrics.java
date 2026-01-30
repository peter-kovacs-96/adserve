package io.adserve.orchestration.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bottleneck/diagnostic metrics for performance analysis.
 * <p>
 * Enable/disable via: METRICS_BOTTLENECK_ENABLED=true/false
 * <p>
 * Metrics:
 * - ad_requests_active: Currently in-flight requests (queuing indicator)
 * - ad_phase_duration{phase}: Duration of each processing phase
 */
@Component
public class BottleneckMetrics {

    @Getter
    private final boolean enabled;

    private final AtomicInteger activeRequests = new AtomicInteger(0);

    private final Timer phaseInternalTimer;
    private final Timer phaseMlTimer;
    private final Timer phasePartnerTimer;

    public BottleneckMetrics(MetricsProperties properties, MeterRegistry meterRegistry) {
        this.enabled = properties.bottleneckEnabled();

        if (enabled) {
            Gauge.builder("ad_requests_active", activeRequests, AtomicInteger::get)
                    .description("Number of ad requests currently being processed")
                    .register(meterRegistry);

            this.phaseInternalTimer = Timer.builder("ad_phase_duration")
                    .tag("phase", "internal_grpc")
                    .description("Duration of internal gRPC calls phase")
                    .publishPercentileHistogram()
                    .register(meterRegistry);

            this.phaseMlTimer = Timer.builder("ad_phase_duration")
                    .tag("phase", "ml_inference")
                    .description("Duration of ML inference phase")
                    .publishPercentileHistogram()
                    .register(meterRegistry);

            this.phasePartnerTimer = Timer.builder("ad_phase_duration")
                    .tag("phase", "partner_bidding")
                    .description("Duration of partner bidding phase")
                    .publishPercentileHistogram()
                    .register(meterRegistry);
        } else {
            this.phaseInternalTimer = null;
            this.phaseMlTimer = null;
            this.phasePartnerTimer = null;
        }
    }

    public int incrementActiveRequests() {
        if (enabled) {
            return activeRequests.incrementAndGet();
        }
        return -1;
    }

    public int decrementActiveRequests() {
        if (enabled) {
            return activeRequests.decrementAndGet();
        }
        return -1;
    }

    public void recordPhaseInternal(Duration duration) {
        if (enabled) {
            phaseInternalTimer.record(duration);
        }
    }

    public void recordPhaseMl(Duration duration) {
        if (enabled) {
            phaseMlTimer.record(duration);
        }
    }

    public void recordPhasePartner(Duration duration) {
        if (enabled) {
            phasePartnerTimer.record(duration);
        }
    }
}
