package io.adserve.orchestration.metrics;

import io.micrometer.core.instrument.Counter;
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
 * - ad_grpc_call_duration{service}: Duration of individual gRPC calls
 * - ad_http_call_duration{service}: Duration of HTTP calls (ML inference)
 * - ad_partner_call_duration{partner,status}: Duration of partner calls
 * - ad_partner_calls_total{partner,status}: Partner call counts
 */
@Component
public class BottleneckMetrics {

    @Getter
    private final boolean enabled;
    private final MeterRegistry meterRegistry;

    // Active requests gauge
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    // Phase timers
    private final Timer phaseInternalTimer;
    private final Timer phaseMlTimer;
    private final Timer phasePartnerTimer;

    // gRPC service timers
    private final Timer grpcUserTimer;
    private final Timer grpcSegmentTimer;
    private final Timer grpcTargetingTimer;

    // HTTP service timer
    private final Timer httpMlTimer;

    public BottleneckMetrics(MetricsProperties properties, MeterRegistry meterRegistry) {
        this.enabled = properties.isBottleneckEnabled();
        this.meterRegistry = meterRegistry;

        if (enabled) {
            // Active requests gauge
            Gauge.builder("ad_requests_active", activeRequests, AtomicInteger::get)
                    .description("Number of ad requests currently being processed")
                    .register(meterRegistry);

            // Phase timers (with histogram buckets for percentile queries)
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

            // gRPC service timers
            this.grpcUserTimer = Timer.builder("ad_grpc_call_duration")
                    .tag("service", "user")
                    .description("Duration of user service gRPC call")
                    .publishPercentileHistogram()
                    .register(meterRegistry);

            this.grpcSegmentTimer = Timer.builder("ad_grpc_call_duration")
                    .tag("service", "segment")
                    .description("Duration of segment service gRPC call")
                    .publishPercentileHistogram()
                    .register(meterRegistry);

            this.grpcTargetingTimer = Timer.builder("ad_grpc_call_duration")
                    .tag("service", "targeting")
                    .description("Duration of targeting service gRPC call")
                    .publishPercentileHistogram()
                    .register(meterRegistry);

            // HTTP ML timer
            this.httpMlTimer = Timer.builder("ad_http_call_duration")
                    .tag("service", "ml_inference")
                    .description("Duration of ML inference HTTP call")
                    .publishPercentileHistogram()
                    .register(meterRegistry);
        } else {
            this.phaseInternalTimer = null;
            this.phaseMlTimer = null;
            this.phasePartnerTimer = null;
            this.grpcUserTimer = null;
            this.grpcSegmentTimer = null;
            this.grpcTargetingTimer = null;
            this.httpMlTimer = null;
        }
    }

    // Active requests tracking
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

    public int getActiveRequests() {
        return activeRequests.get();
    }

    // Phase timing
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

    // gRPC call timing
    public void recordGrpcUserCall(Duration duration) {
        if (enabled) {
            grpcUserTimer.record(duration);
        }
    }

    public void recordGrpcSegmentCall(Duration duration) {
        if (enabled) {
            grpcSegmentTimer.record(duration);
        }
    }

    public void recordGrpcTargetingCall(Duration duration) {
        if (enabled) {
            grpcTargetingTimer.record(duration);
        }
    }

    // HTTP call timing
    public void recordHttpMlCall(Duration duration) {
        if (enabled) {
            httpMlTimer.record(duration);
        }
    }

    // Partner call timing and counting
    public void recordPartnerCall(String partnerId, String status, Duration duration) {
        if (enabled) {
            Timer.builder("ad_partner_call_duration")
                    .tag("partner", partnerId)
                    .tag("status", status)
                    .publishPercentileHistogram()
                    .register(meterRegistry)
                    .record(duration);

            Counter.builder("ad_partner_calls_total")
                    .tag("partner", partnerId)
                    .tag("status", status)
                    .register(meterRegistry)
                    .increment();
        }
    }
}
