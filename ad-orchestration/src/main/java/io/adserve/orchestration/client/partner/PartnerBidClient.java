package io.adserve.orchestration.client.partner;

import io.adserve.orchestration.openrtb.BidRequest;
import io.adserve.orchestration.openrtb.BidResponse;

/**
 * Common contract for all demand partner clients.
 * Accepts an OpenRTB 2.6 BidRequest and returns a BidResponse.
 */
public interface PartnerBidClient {
    BidResponse bid(BidRequest request);
}
