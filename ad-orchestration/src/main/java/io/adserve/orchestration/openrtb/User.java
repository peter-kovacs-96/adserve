package io.adserve.orchestration.openrtb;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record User(
        String id,
        List<Data> data,
        String consent,
        Integer yob,
        String gender
) {}
