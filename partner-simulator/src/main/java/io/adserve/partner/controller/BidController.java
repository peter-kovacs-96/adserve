package io.adserve.partner.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/partners")
public class BidController {

    private static final Logger log = LoggerFactory.getLogger(BidController.class);

    private static final Set<String> VALID_PARTNERS = Set.of(
            "nike", "adidas", "puma", "underarmour", "newbalance",
            "asics", "reebok", "sketchers", "columbia", "northface"
    );

    @SuppressWarnings("unchecked")
    @PostMapping("/{partnerId}/bid")
    public Map<String, Object> bid(
            @PathVariable String partnerId,
            @RequestBody Map<String, Object> request) {

        var requestId = (String) request.getOrDefault("id", UUID.randomUUID().toString());
        var source = (Map<String, Object>) request.getOrDefault("source", Map.of());
        var traceId = (String) source.getOrDefault("tid", "");

        log.info("Bid request received - partnerId: {}, requestId: {}, traceId: {}", partnerId, requestId, traceId);

        if (!VALID_PARTNERS.contains(partnerId.toLowerCase())) {
            log.warn("Unknown partner: {}", partnerId);
            return Map.of("id", requestId, "seatbid", List.of(), "cur", "USD",
                    "nbr", 2); // OpenRTB nbr: Invalid Request
        }

        // Simulate network latency (20-80ms)
        simulateLatency();

        // Extract impression details for a compliant response
        var imps = (List<Map<String, Object>>) request.getOrDefault("imp", List.of());
        var firstImp = imps.isEmpty() ? Map.<String, Object>of() : imps.getFirst();
        var impId = (String) firstImp.getOrDefault("id", "imp-1");
        var bidfloor = ((Number) firstImp.getOrDefault("bidfloor", 0.0)).doubleValue();

        // Extract banner w/h from the impression
        var banner = (Map<String, Object>) firstImp.getOrDefault("banner", Map.of());
        var bannerW = ((Number) banner.getOrDefault("w", 728)).intValue();
        var bannerH = ((Number) banner.getOrDefault("h", 90)).intValue();

        // Generate a random bid price above the floor ($1.50 - $3.50, min = floor)
        var random = ThreadLocalRandom.current();
        var bidPrice = Math.max(bidfloor, 1.50) + random.nextDouble(2.0);
        bidPrice = Math.round(bidPrice * 100.0) / 100.0;

        var adId = "ad-" + partnerId + "-" + UUID.randomUUID().toString().substring(0, 8);
        var bidId = UUID.randomUUID().toString();

        var response = Map.of(
                "id", requestId,
                "seatbid", List.of(Map.of(
                        "seat", partnerId,
                        "bid", List.of(Map.ofEntries(
                                Map.entry("id", bidId),
                                Map.entry("impid", impId),
                                Map.entry("price", bidPrice),
                                Map.entry("adid", adId),
                                Map.entry("nurl", "https://" + partnerId + ".com/win?auction=${AUCTION_ID}&price=${AUCTION_PRICE}&bid=${AUCTION_BID_ID}"),
                                Map.entry("lurl", "https://" + partnerId + ".com/loss?auction=${AUCTION_ID}&reason=${AUCTION_LOSS}"),
                                Map.entry("burl", "https://" + partnerId + ".com/billing?auction=${AUCTION_ID}&price=${AUCTION_PRICE}"),
                                Map.entry("adm", "<ad>" + partnerId + " creative</ad>"),
                                Map.entry("adomain", List.of(partnerId + ".com")),
                                Map.entry("crid", "creative-" + partnerId),
                                Map.entry("w", bannerW),
                                Map.entry("h", bannerH)
                        ))
                )),
                "cur", "USD"
        );

        log.info("Bid response sent - partnerId: {}, price: {}, adId: {}, impId: {}, size: {}x{}, traceId: {}",
                partnerId, bidPrice, adId, impId, bannerW, bannerH, traceId);

        return response;
    }

    private void simulateLatency() {
        try {
            var delay = ThreadLocalRandom.current().nextInt(20, 81);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
