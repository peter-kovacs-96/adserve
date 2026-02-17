package io.adserve.orchestration.openrtb;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Banner(
        Integer w,
        Integer h,
        List<Format> format,
        List<Integer> btype,
        List<Integer> battr,
        Integer pos
) {}
