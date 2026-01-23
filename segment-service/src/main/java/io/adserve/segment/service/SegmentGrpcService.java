package io.adserve.segment.service;

import io.adserve.segment.grpc.*;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GrpcService
public class SegmentGrpcService extends SegmentServiceGrpc.SegmentServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(SegmentGrpcService.class);

    @Override
    public void getSegments(GetSegmentsRequest request, StreamObserver<GetSegmentsResponse> responseObserver) {
        log.info("GetSegments request received - userId: {}, traceId: {}, requestId: {}",
                request.getUserId(), request.getTraceId(), request.getRequestId());

        try {
            var sportsFan = Segment.newBuilder()
                    .setId("seg-001")
                    .setName("sports_fan")
                    .setType(SegmentType.INTEREST)
                    .setScore(0.85)
                    .setTimestamp(System.currentTimeMillis())
                    .build();

            var highIncome = Segment.newBuilder()
                    .setId("seg-002")
                    .setName("high_income")
                    .setType(SegmentType.DEMOGRAPHIC)
                    .setScore(0.92)
                    .setTimestamp(System.currentTimeMillis())
                    .build();

            var techEnthusiast = Segment.newBuilder()
                    .setId("seg-003")
                    .setName("tech_enthusiast")
                    .setType(SegmentType.BEHAVIORAL)
                    .setScore(0.78)
                    .setTimestamp(System.currentTimeMillis())
                    .build();

            var response = GetSegmentsResponse.newBuilder()
                    .addSegments(sportsFan)
                    .addSegments(highIncome)
                    .addSegments(techEnthusiast)
                    .setTraceId(request.getTraceId())
                    .setRequestId(request.getRequestId())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("GetSegments response sent - userId: {}, segmentCount: {}, traceId: {}",
                    request.getUserId(), response.getSegmentsCount(), request.getTraceId());

        } catch (Exception e) {
            log.error("Error processing GetSegments request - traceId: {}", request.getTraceId(), e);
            responseObserver.onError(e);
        }
    }
}
