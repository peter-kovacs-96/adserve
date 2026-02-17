package io.adserve.orchestration.openrtb;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Site(
        String id,
        String name,
        String domain,
        String page,
        String ref,
        List<String> cat,
        Publisher publisher
) {}
