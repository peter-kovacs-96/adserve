package io.adserve.orchestration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grpc.client")
public record GrpcClientProperties(
        ServiceAddress userService,
        ServiceAddress segmentService,
        ServiceAddress targetingService
) {
    public record ServiceAddress(String host, int port) {}
}
