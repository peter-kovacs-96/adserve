package io.adserve.orchestration.openrtb;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record App(
        String id,
        String name,
        String bundle,
        String storeurl,
        String domain,
        List<String> cat,
        String ver,
        Publisher publisher
) {}
