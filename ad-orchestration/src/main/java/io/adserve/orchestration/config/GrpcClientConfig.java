package io.adserve.orchestration.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;

@Configuration
@ImportGrpcClients(target = "user-service", basePackages = "io.adserve.user.grpc")
@ImportGrpcClients(target = "segment-service", basePackages = "io.adserve.segment.grpc")
@ImportGrpcClients(target = "targeting-service", basePackages = "io.adserve.targeting.grpc")
public class GrpcClientConfig {
}
