package io.adserve.orchestration.service;

public final class AuctionMacros {

    private AuctionMacros() {}

    public static String substituteWin(String url, String auctionId, double price,
            String bidId, String currency) {
        if (url == null || url.isEmpty()) return url;
        return url
                .replace("${AUCTION_ID}", auctionId)
                .replace("${AUCTION_PRICE}", String.format("%.2f", price))
                .replace("${AUCTION_BID_ID}", bidId != null ? bidId : "")
                .replace("${AUCTION_CURRENCY}", currency != null ? currency : "USD");
    }

    public static String substituteLoss(String url, String auctionId, double bidPrice,
            int lossReason) {
        if (url == null || url.isEmpty()) return url;
        return url
                .replace("${AUCTION_ID}", auctionId)
                .replace("${AUCTION_PRICE}", String.format("%.2f", bidPrice))
                .replace("${AUCTION_LOSS}", String.valueOf(lossReason));
    }
}
