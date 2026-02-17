package io.adserve.orchestration.controller;

import io.adserve.orchestration.client.MlInferenceClient;
import io.adserve.orchestration.client.MlPredictionRequest;
import io.adserve.orchestration.client.MlPredictionResponse;
import io.adserve.orchestration.metrics.AdMetrics;
import io.adserve.orchestration.service.AuctionResult;
import io.adserve.orchestration.service.AuctionService;
import io.adserve.orchestration.service.BidRequestBuilder;
import io.adserve.segment.grpc.GetSegmentsRequest;
import io.adserve.segment.grpc.GetSegmentsResponse;
import io.adserve.segment.grpc.SegmentServiceGrpc;
import io.adserve.targeting.grpc.GetTargetingRulesRequest;
import io.adserve.targeting.grpc.GetTargetingRulesResponse;
import io.adserve.targeting.grpc.TargetingServiceGrpc;
import io.adserve.user.grpc.GetUserRequest;
import io.adserve.user.grpc.GetUserResponse;
import io.adserve.user.grpc.UserServiceGrpc;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.StructuredTaskScope;

@Slf4j
@RestController
@RequestMapping("/api/v1/ads")
public class AdController {

    private final UserServiceGrpc.UserServiceBlockingStub userServiceStub;
    private final SegmentServiceGrpc.SegmentServiceBlockingStub segmentServiceStub;
    private final TargetingServiceGrpc.TargetingServiceBlockingStub targetingServiceStub;
    private final MlInferenceClient mlInferenceClient;
    private final AdMetrics adMetrics;
    private final BidRequestBuilder bidRequestBuilder;
    private final AuctionService auctionService;

    public AdController(
            UserServiceGrpc.UserServiceBlockingStub userServiceStub,
            SegmentServiceGrpc.SegmentServiceBlockingStub segmentServiceStub,
            TargetingServiceGrpc.TargetingServiceBlockingStub targetingServiceStub,
            MlInferenceClient mlInferenceClient,
            AdMetrics adMetrics,
            BidRequestBuilder bidRequestBuilder,
            AuctionService auctionService) {
        this.userServiceStub = userServiceStub;
        this.segmentServiceStub = segmentServiceStub;
        this.targetingServiceStub = targetingServiceStub;
        this.mlInferenceClient = mlInferenceClient;
        this.adMetrics = adMetrics;
        this.bidRequestBuilder = bidRequestBuilder;
        this.auctionService = auctionService;
    }

    @PostMapping("/request")
    public AdResponse requestAd(@RequestBody AdRequest request, HttpServletRequest httpRequest) {
        var startTime = Instant.now();
        var requestId = UUID.randomUUID().toString();
        var traceId = UUID.randomUUID().toString();
        var userId = request.userId() != null ? request.userId() : "unknown";

        var userAgent = httpRequest.getHeader("User-Agent");
        var forwarded = httpRequest.getHeader("X-Forwarded-For");
        var ip = forwarded != null ? forwarded.split(",")[0].trim() : httpRequest.getRemoteAddr();

        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {

            var userTask = scope.fork(() -> callUserService(userId, traceId, requestId));
            var segmentTask = scope.fork(() -> callSegmentService(userId, traceId, requestId));
            var targetingTask = scope.fork(() -> callTargetingService(userId, traceId, requestId));
            scope.join();

            var userResponse = userTask.get();
            var segmentResponse = segmentTask.get();
            var targetingResponse = targetingTask.get();

            var mlPrediction = callMlService(userId, traceId, segmentResponse);

            var bidRequest = bidRequestBuilder.build(requestId, traceId, request, userAgent, ip,
                    userResponse, segmentResponse);
            var auctionResult = auctionService.runAuction(bidRequest, traceId);

            adMetrics.recordAuctionWin(auctionResult.winnerId());

            var processingTime = Duration.between(startTime, Instant.now());

            log.info("Ad request completed | requestId={} | traceId={} | totalMs={} | winner={} | price=${}",
                    requestId, traceId, processingTime.toMillis(),
                    auctionResult.winnerId(), String.format("%.2f", auctionResult.winningPrice()));

            return buildResponse(requestId, traceId, processingTime.toMillis(),
                    userResponse, segmentResponse, targetingResponse, mlPrediction, auctionResult);

        } catch (Exception e) {
            var processingTime = Duration.between(startTime, Instant.now());
            log.error("Ad request failed | requestId={} | traceId={} | timeMs={} | error={}",
                    requestId, traceId, processingTime.toMillis(), e.getMessage(), e);
            throw new RuntimeException("Ad request failed: " + e.getMessage(), e);
        }
    }

    private GetUserResponse callUserService(String userId, String traceId, String requestId) {
        return userServiceStub.getUser(GetUserRequest.newBuilder()
                .setUserId(userId)
                .setTraceId(traceId)
                .setRequestId(requestId)
                .build());
    }

    private GetSegmentsResponse callSegmentService(String userId, String traceId, String requestId) {
        return segmentServiceStub.getSegments(GetSegmentsRequest.newBuilder()
                .setUserId(userId)
                .setTraceId(traceId)
                .setRequestId(requestId)
                .build());
    }

    private GetTargetingRulesResponse callTargetingService(String userId, String traceId, String requestId) {
        return targetingServiceStub.getTargetingRules(GetTargetingRulesRequest.newBuilder()
                .setUserId(userId)
                .setTraceId(traceId)
                .setRequestId(requestId)
                .build());
    }

    private MlPredictionResponse callMlService(String userId, String traceId, GetSegmentsResponse segmentResponse) {
        var segmentNames = segmentResponse.getSegmentsList().stream()
                .map(io.adserve.segment.grpc.Segment::getName)
                .toList();
        return mlInferenceClient.predict(new MlPredictionRequest(userId, traceId, segmentNames));
    }

    private AdResponse buildResponse(
            String requestId, String traceId, long processingTimeMs,
            GetUserResponse userResponse, GetSegmentsResponse segmentResponse,
            GetTargetingRulesResponse targetingResponse, MlPredictionResponse mlPrediction,
            AuctionResult auction) {

        var user = userResponse.getUser();
        return new AdResponse(
                requestId, traceId, "success", processingTimeMs,
                new AdResponse.Ad(auction.winnerId(), auction.winningPrice(), auction.adId(), auction.creativeUrl()),
                new AdResponse.User(user.getUserId(), user.getName(),
                        user.getDemographics().getCountry(), user.getDevice().getDeviceType()),
                segmentResponse.getSegmentsList().stream()
                        .map(s -> new AdResponse.SegmentInfo(s.getId(), s.getName(), s.getType().name(), s.getScore()))
                        .toList(),
                targetingResponse.getRulesList().stream()
                        .map(r -> new AdResponse.TargetingRule(r.getId(), r.getName(), r.getPriority(), r.getEligiblePartnersList()))
                        .toList(),
                new AdResponse.Prediction(mlPrediction.ctr(), mlPrediction.cvr(), mlPrediction.modelVersion()),
                new AdResponse.Metadata(3, true, auction.partnersCalled(), auction.bidsReceived())
        );
    }
}
