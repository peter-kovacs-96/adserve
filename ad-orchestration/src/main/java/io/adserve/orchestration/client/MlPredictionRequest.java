package io.adserve.orchestration.client;

import java.util.List;

public record MlPredictionRequest(
        String userId,
        String traceId,
        List<String> segments
) {}
