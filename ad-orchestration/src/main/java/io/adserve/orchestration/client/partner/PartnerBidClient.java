package io.adserve.orchestration.client.partner;


import java.util.Map;

/**
 * Common contract remains the same.
 */
public interface PartnerBidClient {
    Map<String, Object> bid(Map<String, Object> request);
}