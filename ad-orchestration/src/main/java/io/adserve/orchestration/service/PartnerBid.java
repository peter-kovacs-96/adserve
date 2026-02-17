package io.adserve.orchestration.service;

public record PartnerBid(String partnerId, double price, String adId,
                          String nurl, String lurl, String bidId) {}
