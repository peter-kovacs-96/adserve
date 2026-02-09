package io.adserve.orchestration.client.partner;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

@HttpExchange
public interface AdidasClient extends PartnerBidClient {

    @Override
    @PostExchange("/bid")
    //@ConcurrencyLimit(100)
    @Retryable(
            maxRetriesString = "${resilience.retry.partners.max-retries}",
            delayString = "${resilience.retry.partners.delay}"
    )
    Map<String, Object> bid(@RequestBody Map<String, Object> request);
}