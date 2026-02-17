package io.adserve.orchestration.service;

import io.adserve.orchestration.client.partner.PartnerClientRegistry;
import io.adserve.orchestration.metrics.AdMetrics;
import io.adserve.orchestration.openrtb.BidRequest;
import io.adserve.orchestration.openrtb.BidResponse;
import io.adserve.orchestration.openrtb.OpenRtb;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

@Slf4j
@Service
public class AuctionService {

    private final PartnerClientRegistry partnerClientRegistry;
    private final AdMetrics adMetrics;
    private final NotificationService notificationService;

    public AuctionService(PartnerClientRegistry partnerClientRegistry,
                          AdMetrics adMetrics,
                          NotificationService notificationService) {
        this.partnerClientRegistry = partnerClientRegistry;
        this.adMetrics = adMetrics;
        this.notificationService = notificationService;
    }

    public AuctionResult runAuction(BidRequest bidRequest, String traceId) {
        var bids = new ArrayList<PartnerBid>();
        var partnerIds = partnerClientRegistry.getPartnerIds();

        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<BidResponse>allSuccessfulOrThrow())) {

            var tasks = new HashMap<String, Subtask<BidResponse>>();
            for (var partnerId : partnerIds) {
                tasks.put(partnerId, scope.fork(() ->
                        partnerClientRegistry.get(partnerId).bid(bidRequest)));
            }

            try { scope.join(); } catch (Exception e) { /* timeout - continue with partial results */ }

            for (var entry : tasks.entrySet()) {
                if (entry.getValue().state() == Subtask.State.SUCCESS) {
                    var bid = extractBid(entry.getKey(), entry.getValue().get(), bidRequest);
                    if (bid != null) bids.add(bid);
                }
            }
        } catch (Exception e) {
            log.warn("Partner bidding failed | traceId={} | error={}", traceId, e.getMessage());
        }

        var winner = bids.stream()
                .max(Comparator.comparingDouble(PartnerBid::price))
                .orElse(new PartnerBid("none", 0.0, "", "", "", ""));

        if (!winner.partnerId().equals("none")) {
            notificationService.notifyWin(winner.nurl(), bidRequest.id(),
                    winner.price(), winner.bidId(), "USD");
        }

        for (var bid : bids) {
            if (!bid.partnerId().equals(winner.partnerId())) {
                notificationService.notifyLoss(bid.lurl(), bidRequest.id(),
                        bid.price(), OpenRtb.LossReason.LOST_TO_HIGHER_BID);
            }
        }

        return new AuctionResult(winner.partnerId(), winner.price(), winner.adId(),
                winner.nurl(), partnerIds.size(), bids.size());
    }

    private PartnerBid extractBid(String partnerId, BidResponse response, BidRequest bidRequest) {
        if (response.seatbid() == null || response.seatbid().isEmpty()) {
            if (response.nbr() != null) {
                log.debug("No-bid from {} | reason={}", partnerId, response.nbr());
                adMetrics.recordNoBid(partnerId, response.nbr());
            }
            return null;
        }

        var firstSeat = response.seatbid().getFirst();
        if (firstSeat.bid() == null || firstSeat.bid().isEmpty()) return null;

        var bid = firstSeat.bid().getFirst();

        if (!BidValidator.isValid(bid, bidRequest)) {
            log.debug("Invalid bid from {} | impid={} price={}", partnerId, bid.impid(), bid.price());
            adMetrics.recordInvalidBid(partnerId);
            return null;
        }

        return new PartnerBid(partnerId, bid.price(),
                bid.adid() != null ? bid.adid() : "",
                bid.nurl() != null ? bid.nurl() : "",
                bid.lurl() != null ? bid.lurl() : "",
                bid.id() != null ? bid.id() : "");
    }
}
