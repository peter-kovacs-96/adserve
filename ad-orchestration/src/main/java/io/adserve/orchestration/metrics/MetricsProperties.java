package io.adserve.orchestration.metrics;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "metrics")
public record MetricsProperties(
        boolean businessEnabled,
        boolean bottleneckEnabled
) {}
