package io.adserve.orchestration.openrtb;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Geo(
        Double lat,
        Double lon,
        String country,
        String region,
        String city,
        Integer type
) {}
