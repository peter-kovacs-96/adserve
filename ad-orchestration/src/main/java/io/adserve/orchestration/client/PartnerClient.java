package io.adserve.orchestration.client;

import io.adserve.orchestration.config.HttpClientProperties;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class PartnerClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final RetryRegistry retryRegistry;
    private final MeterRegistry meterRegistry;

    public PartnerClient(
            HttpClientProperties properties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            BulkheadRegistry bulkheadRegistry,
            RetryRegistry retryRegistry,
            MeterRegistry meterRegistry) {

        var config = properties.partner();

        // JDK HttpClient with virtual thread executor for Loom compatibility
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(config.readTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(config.baseUrl())
                .requestFactory(requestFactory)
                .build();

        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.retryRegistry = retryRegistry;
        this.meterRegistry = meterRegistry;

        log.info("PartnerClient initialized | baseUrl={} | connectTimeout={} | readTimeout={}",
                config.baseUrl(), config.connectTimeout(), config.readTimeout());
    }

    public Map<String, Object> bid(String partnerId, Map<String, Object> request) {
        var circuitBreaker = circuitBreakerRegistry.circuitBreaker(partnerId);
        var bulkhead = bulkheadRegistry.bulkhead(partnerId);
        var retry = retryRegistry.retry(partnerId);

        var sample = Timer.start(meterRegistry);
        try {
            var result = retry.executeSupplier(
                    () -> circuitBreaker.executeSupplier(
                            () -> bulkhead.executeSupplier(
                                    () -> doHttpCall(partnerId, request))));
            sample.stop(partnerTimer(partnerId, "success"));
            meterRegistry.counter("ad_partner_calls", "partner", partnerId, "status", "success").increment();
            return result;
        } catch (Exception e) {
            sample.stop(partnerTimer(partnerId, "error"));
            meterRegistry.counter("ad_partner_calls", "partner", partnerId, "status", "error").increment();
            throw e;
        }
    }

    private Timer partnerTimer(String partnerId, String status) {
        return Timer.builder("ad_partner_call_duration")
                .tag("partner", partnerId)
                .tag("status", status)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private Map<String, Object> doHttpCall(String partnerId, Map<String, Object> request) {
        return restClient.post()
                .uri("/partners/{partnerId}/bid", partnerId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MAP_TYPE);
    }
}
