package io.adserve.orchestration.openrtb;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Device(
        String ua,
        Geo geo,
        String ip,
        Integer devicetype,
        String make,
        String model,
        String os,
        String osv,
        String language,
        Integer js,
        Integer w,
        Integer h,
        Integer dnt,
        Integer lmt
) {}
