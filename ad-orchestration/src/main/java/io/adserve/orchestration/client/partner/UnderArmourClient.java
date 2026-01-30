package io.adserve.orchestration.client.partner;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

@HttpExchange
public interface UnderArmourClient extends PartnerBidClient {

    @Override
    @PostExchange("/bid")
    @CircuitBreaker(name = "underarmour")
    @Bulkhead(name = "underarmour")
    @Retry(name = "underarmour")
    Map<String, Object> bid(@RequestBody Map<String, Object> request);
}
