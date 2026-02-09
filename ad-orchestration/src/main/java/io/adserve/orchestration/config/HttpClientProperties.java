package io.adserve.orchestration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "http.client")
public record HttpClientProperties(
        Duration connectTimeout,
        Duration readTimeout,
        Map<String, String> groups
) {}