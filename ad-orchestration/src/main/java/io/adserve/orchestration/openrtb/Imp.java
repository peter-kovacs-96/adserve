package io.adserve.orchestration.openrtb;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Imp(
        String id,
        Banner banner,
        double bidfloor,
        String bidfloorcur,
        Integer secure,
        Integer rwdd,
        String tagid,
        Integer instl
) {}
