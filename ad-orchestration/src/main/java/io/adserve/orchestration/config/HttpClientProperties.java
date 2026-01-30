package io.adserve.orchestration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "http.client")
public record HttpClientProperties(
        ServiceConfig mlInference,
        Map<String, ServiceConfig> partners
) {
    public record ServiceConfig(
            String baseUrl,
            Duration connectTimeout,
            Duration readTimeout
    ) {}
}
