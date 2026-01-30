package io.adserve.orchestration.client.partner;

import java.util.Map;

/**
 * Common contract for all partner bid clients.
 */
public interface PartnerBidClient {
    Map<String, Object> bid(Map<String, Object> request);
}
