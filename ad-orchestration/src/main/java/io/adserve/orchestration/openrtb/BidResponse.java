package io.adserve.orchestration.openrtb;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BidResponse(
        String id,
        List<SeatBid> seatbid,
        String cur,
        Integer nbr
) {}
