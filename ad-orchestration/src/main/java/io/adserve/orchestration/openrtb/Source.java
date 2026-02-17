package io.adserve.orchestration.openrtb;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Source(
        Integer fd,
        Schain schain,
        String tid,
        String pchain
) {}
