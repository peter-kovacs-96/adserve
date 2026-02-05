package io.adserve.orchestration.client;

import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

@HttpExchange
public interface MlInferenceClient {
    @ConcurrencyLimit(20)
    @Retryable(
            maxRetriesString = "${resilience.retry.ml-inference.max-retries:3}",
            delayString = "${resilience.retry.ml-inference.delay:30ms}"
    )
    @PostExchange("/api/v1/predict")
    Map<String, Object> predict(@RequestBody Map<String, Object> request);
}