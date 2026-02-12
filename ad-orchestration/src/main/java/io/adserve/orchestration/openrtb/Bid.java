package io.adserve.orchestration.openrtb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Bid(
        String id,
        String impid,
        double price,
        String adm,
        String adid,
        List<String> adomain,
        String crid,
        String nurl,
        String lurl,
        String burl,
        Integer w,
        Integer h,
        @JsonProperty("deal_id")
        String dealId
) {}
