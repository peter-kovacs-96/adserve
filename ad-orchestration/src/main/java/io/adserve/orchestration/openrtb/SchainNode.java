package io.adserve.orchestration.openrtb;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SchainNode(
        String asi,
        String sid,
        int hp,
        String rid
) {}
