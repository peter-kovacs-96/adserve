package io.adserve.orchestration.client.partner;

import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

@HttpExchange
public interface NorthFaceClient extends PartnerBidClient {
    @Override
    @PostExchange("/bid")
    @ConcurrencyLimit(100)
    @Retryable(
            maxRetriesString = "${resilience.retry.partners.max-retries:1}",
            delayString = "${resilience.retry.partners.delay:0ms}"
    )
    Map<String, Object> bid(@RequestBody Map<String, Object> request);
}