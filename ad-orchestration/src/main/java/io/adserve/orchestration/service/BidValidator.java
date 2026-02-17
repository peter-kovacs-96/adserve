package io.adserve.orchestration.service;

import io.adserve.orchestration.openrtb.BidRequest;
import io.adserve.orchestration.openrtb.Bid;
import io.adserve.orchestration.openrtb.Imp;

import java.util.List;
import java.util.Set;

public final class BidValidator {

    private BidValidator() {}

    public static boolean isValid(Bid bid, BidRequest request) {
        if (!matchesImpression(bid.impid(), request.imp())) return false;
        if (!meetsFloor(bid.price(), bid.impid(), request.imp())) return false;
        return !isBlocked(bid.adomain(), request.badv());
    }

    private static boolean matchesImpression(String impid, List<Imp> imps) {
        if (impid == null || imps == null) return false;
        return imps.stream().anyMatch(imp -> imp.id().equals(impid));
    }

    private static boolean meetsFloor(double price, String impid, List<Imp> imps) {
        return imps.stream()
                .filter(imp -> imp.id().equals(impid))
                .findFirst()
                .map(imp -> price >= imp.bidfloor())
                .orElse(false);
    }

    private static boolean isBlocked(List<String> adomain, List<String> badv) {
        if (adomain == null || badv == null) return false;
        var blocked = Set.copyOf(badv);
        return adomain.stream().anyMatch(blocked::contains);
    }
}
