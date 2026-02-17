package io.adserve.orchestration.controller;

import java.util.List;

public record AdResponse(
        String requestId, String traceId, String status, long processingTimeMs,
        Ad ad, User user, List<SegmentInfo> segments, List<TargetingRule> targetingRules,
        Prediction prediction, Metadata metadata) {

    public record Ad(String partnerId, double bidPrice, String adId, String creativeUrl) {}
    public record User(String userId, String name, String country, String deviceType) {}
    public record SegmentInfo(String id, String name, String type, double score) {}
    public record TargetingRule(String id, String name, int priority, List<String> eligiblePartners) {}
    public record Prediction(double ctr, double cvr, String modelVersion) {}
    public record Metadata(int internalServicesCalled, boolean mlServiceCalled, int partnersCalled, int bidsReceived) {}
}
