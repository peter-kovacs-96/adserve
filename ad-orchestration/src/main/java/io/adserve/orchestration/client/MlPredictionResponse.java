package io.adserve.orchestration.client;

public record MlPredictionResponse(
        double ctr,
        double cvr,
        String modelVersion,
        String traceId
) {}
