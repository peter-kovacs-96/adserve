package io.adserve.orchestration.service;

public record AuctionResult(String winnerId, double winningPrice, String adId, String creativeUrl,
                             int partnersCalled, int bidsReceived) {}
