package io.adserve.orchestration.client;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

@HttpExchange
public interface MlInferenceClient {

    //@ConcurrencyLimit(200)
    @Retryable(
            maxRetriesString = "${resilience.retry.ml-inference.max-retries}",
            delayString = "${resilience.retry.ml-inference.delay}"
    )
    @PostExchange("/api/v1/predict")
    Map<String, Object> predict(@RequestBody Map<String, Object> request);
}