package io.adserve.orchestration.openrtb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Regs(
        Integer coppa,
        Integer gdpr,
        @JsonProperty("us_privacy")
        String usPrivacy,
        String gpp
) {}
