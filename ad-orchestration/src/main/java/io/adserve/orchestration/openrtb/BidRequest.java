package io.adserve.orchestration.openrtb;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BidRequest(
        String id,
        List<Imp> imp,
        Site site,
        App app,
        Device device,
        User user,
        Source source,
        Regs regs,
        Integer at,
        Integer tmax,
        List<String> cur,
        List<String> bcat,
        List<String> badv,
        Integer test
) {}
