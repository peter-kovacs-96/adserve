package io.adserve.orchestration.config;

import io.adserve.segment.grpc.SegmentServiceGrpc;
import io.adserve.targeting.grpc.TargetingServiceGrpc;
import io.adserve.user.grpc.UserServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class GrpcClientConfig {

    @Value("${grpc.client.user-service.address:localhost:9090}")
    private String userServiceAddress;

    @Value("${grpc.client.segment-service.address:localhost:9091}")
    private String segmentServiceAddress;

    @Value("${grpc.client.targeting-service.address:localhost:9092}")
    private String targetingServiceAddress;

    @Value("${grpc.client.deadline-ms:10}")
    private long deadlineMs;

    private ManagedChannel userChannel;
    private ManagedChannel segmentChannel;
    private ManagedChannel targetingChannel;

    @Bean
    public ManagedChannel userChannel() {
        userChannel = createChannel(userServiceAddress);
        return userChannel;
    }

    @Bean
    public ManagedChannel segmentChannel() {
        segmentChannel = createChannel(segmentServiceAddress);
        return segmentChannel;
    }

    @Bean
    public ManagedChannel targetingChannel() {
        targetingChannel = createChannel(targetingServiceAddress);
        return targetingChannel;
    }

    @Bean
    public UserServiceGrpc.UserServiceBlockingStub userServiceStub(ManagedChannel userChannel) {
        return UserServiceGrpc.newBlockingStub(userChannel);
    }

    @Bean
    public SegmentServiceGrpc.SegmentServiceBlockingStub segmentServiceStub(ManagedChannel segmentChannel) {
        return SegmentServiceGrpc.newBlockingStub(segmentChannel);
    }

    @Bean
    public TargetingServiceGrpc.TargetingServiceBlockingStub targetingServiceStub(ManagedChannel targetingChannel) {
        return TargetingServiceGrpc.newBlockingStub(targetingChannel);
    }

    private ManagedChannel createChannel(String address) {
        String[] parts = address.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @PreDestroy
    public void shutdown() {
        shutdownChannel(userChannel);
        shutdownChannel(segmentChannel);
        shutdownChannel(targetingChannel);
    }

    private void shutdownChannel(ManagedChannel channel) {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            try {
                channel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        }
    }
}
