package io.adserve.orchestration.client.partner;

import io.adserve.orchestration.openrtb.BidRequest;
import io.adserve.orchestration.openrtb.BidResponse;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface NikeClient extends PartnerBidClient {

    @Override
    @PostExchange("/bid")
    //@ConcurrencyLimit(100)
    @Retryable(
            maxRetriesString = "${resilience.retry.partners.max-retries}",
            delayString = "${resilience.retry.partners.delay}"
    )
    BidResponse bid(@RequestBody BidRequest request);
}
