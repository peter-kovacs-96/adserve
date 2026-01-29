package io.adserve.orchestration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "http.client")
public record HttpClientProperties(
        ServiceConfig mlInference,
        ServiceConfig partner
) {
    public record ServiceConfig(
            String baseUrl,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        public ServiceConfig {
            connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofMillis(100);
            readTimeout = readTimeout != null ? readTimeout : Duration.ofMillis(50);
        }
    }
}
