package io.adserve.orchestration.openrtb;

public final class OpenRtb {
    private OpenRtb() {}

    /** BidRequest.at */
    public static final class AuctionType {
        public static final int FIRST_PRICE = 1;
        public static final int SECOND_PRICE = 2;
    }

    /** Geo.type */
    public static final class GeoType {
        public static final int GPS = 1;
        public static final int IP = 2;
        public static final int USER_PROVIDED = 3;
    }

    /** Source.fd */
    public static final class FinalDecision {
        public static final int EXCHANGE = 0;
        public static final int UPSTREAM = 1;
    }

    /** BidResponse.nbr */
    public static final class NoBidReason {
        public static final int UNKNOWN_ERROR = 0;
        public static final int TECHNICAL_ERROR = 1;
        public static final int INVALID_REQUEST = 2;
        public static final int KNOWN_SPIDER = 3;
        public static final int SUSPECTED_NON_HUMAN = 4;
        public static final int PROXY_IP = 5;
        public static final int UNSUPPORTED_DEVICE = 6;
        public static final int BLOCKED_PUBLISHER = 7;
        public static final int UNMATCHED_USER = 8;
        public static final int INELIGIBLE_CREATIVE = 10;
    }

    /** lurl macro ${AUCTION_LOSS} */
    public static final class LossReason {
        public static final int INTERNAL_ERROR = 1;
        public static final int IMPRESSION_EXPIRED = 2;
        public static final int INVALID_BID = 3;
        public static final int LOST_TO_HIGHER_BID = 102;
    }
}
