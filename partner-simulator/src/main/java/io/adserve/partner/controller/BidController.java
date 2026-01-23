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

    @PostMapping("/{partnerId}/bid")
    public Map<String, Object> bid(
            @PathVariable String partnerId,
            @RequestBody Map<String, Object> request) {

        var requestId = (String) request.getOrDefault("id", UUID.randomUUID().toString());
        var traceId = (String) request.getOrDefault("traceId", "");

        log.info("Bid request received - partnerId: {}, requestId: {}, traceId: {}",
                partnerId, requestId, traceId);

        if (!VALID_PARTNERS.contains(partnerId.toLowerCase())) {
            log.warn("Unknown partner: {}", partnerId);
            return Map.of(
                    "id", requestId,
                    "seatbid", List.of(),
                    "cur", "USD",
                    "nbr", 2
            );
        }

        // Simulate network latency (20-80ms)
        simulateLatency();

        // Generate random bid price ($1.50 - $3.50)
        var random = ThreadLocalRandom.current();
        var bidPrice = 1.50 + random.nextDouble(2.0);
        bidPrice = Math.round(bidPrice * 100.0) / 100.0;

        var adId = "ad-" + partnerId + "-" + UUID.randomUUID().toString().substring(0, 8);

        var response = Map.of(
                "id", requestId,
                "seatbid", List.of(Map.of(
                        "seat", partnerId,
                        "bid", List.of(Map.of(
                                "id", UUID.randomUUID().toString(),
                                "impid", "imp-1",
                                "price", bidPrice,
                                "adid", adId,
                                "nurl", "https://" + partnerId + ".com/win?id=" + adId,
                                "adm", "<ad>" + partnerId + " creative</ad>",
                                "adomain", List.of(partnerId + ".com"),
                                "crid", "creative-" + partnerId
                        ))
                )),
                "cur", "USD"
        );

        log.info("Bid response sent - partnerId: {}, price: {}, adId: {}, traceId: {}",
                partnerId, bidPrice, adId, traceId);

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
