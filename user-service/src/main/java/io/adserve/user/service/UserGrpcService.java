package io.adserve.user.service;

import io.adserve.user.grpc.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(UserGrpcService.class);

    @Override
    public void getUser(GetUserRequest request, StreamObserver<GetUserResponse> responseObserver) {
        log.info("GetUser request received - userId: {}, traceId: {}, requestId: {}",
                request.getUserId(), request.getTraceId(), request.getRequestId());

        try {
            var demographics = Demographics.newBuilder()
                    .setCountry("USA")
                    .setRegion("CA")
                    .setLanguage("en")
                    .setAgeRangeStart(25)
                    .setAgeRangeEnd(34)
                    .build();

            var deviceInfo = DeviceInfo.newBuilder()
                    .setDeviceType("mobile")
                    .setOs("iOS")
                    .setBrowser("Safari")
                    .build();

            var user = User.newBuilder()
                    .setUserId(request.getUserId())
                    .setName("Mock User")
                    .setEmail("mock.user@example.com")
                    .setDevice(deviceInfo)
                    .setDemographics(demographics)
                    .setCreatedAt(System.currentTimeMillis())
                    .setLastSeenAt(System.currentTimeMillis())
                    .build();

            var response = GetUserResponse.newBuilder()
                    .setUser(user)
                    .setTraceId(request.getTraceId())
                    .setRequestId(request.getRequestId())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("GetUser response sent - userId: {}, traceId: {}",
                    request.getUserId(), request.getTraceId());

        } catch (Exception e) {
            log.error("Error processing GetUser request - traceId: {}", request.getTraceId(), e);
            responseObserver.onError(e);
        }
    }
}
