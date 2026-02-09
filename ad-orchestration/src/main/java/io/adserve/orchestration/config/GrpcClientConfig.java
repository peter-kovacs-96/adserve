package io.adserve.orchestration.config;

import io.adserve.segment.grpc.SegmentServiceGrpc;
import io.adserve.targeting.grpc.TargetingServiceGrpc;
import io.adserve.user.grpc.UserServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Configuration
public class GrpcClientConfig {

    private final GrpcClientProperties properties;
    private final List<ManagedChannel> channels = new ArrayList<>();

    public GrpcClientConfig(GrpcClientProperties properties) {
        this.properties = properties;
    }

    @Bean
    public UserServiceGrpc.UserServiceBlockingStub userServiceStub() {
        return UserServiceGrpc.newBlockingStub(createChannel(properties.userService()));
    }

    @Bean
    public SegmentServiceGrpc.SegmentServiceBlockingStub segmentServiceStub() {
        return SegmentServiceGrpc.newBlockingStub(createChannel(properties.segmentService()));
    }

    @Bean
    public TargetingServiceGrpc.TargetingServiceBlockingStub targetingServiceStub() {
        return TargetingServiceGrpc.newBlockingStub(createChannel(properties.targetingService()));
    }

    private ManagedChannel createChannel(GrpcClientProperties.ServiceAddress address) {
        var channel = ManagedChannelBuilder.forAddress(address.host(), address.port())
                .usePlaintext()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
        channels.add(channel);
        return channel;
    }

    @PreDestroy
    public void shutdown() {
        channels.forEach(channel -> {
            if (!channel.isShutdown()) {
                channel.shutdown();
                try {
                    channel.awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    channel.shutdownNow();
                }
            }
        });
    }
}
