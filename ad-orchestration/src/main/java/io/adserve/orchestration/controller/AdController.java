package io.adserve.orchestration.controller;

import io.adserve.orchestration.client.MlInferenceClient;
import io.adserve.orchestration.client.partner.PartnerClientRegistry;
import io.adserve.orchestration.metrics.BottleneckMetrics;
import io.adserve.orchestration.metrics.BusinessMetrics;
import io.adserve.segment.grpc.GetSegmentsRequest;
import io.adserve.segment.grpc.GetSegmentsResponse;
import io.adserve.segment.grpc.Segment;
import io.adserve.segment.grpc.SegmentServiceGrpc;
import io.adserve.targeting.grpc.GetTargetingRulesRequest;
import io.adserve.targeting.grpc.GetTargetingRulesResponse;
import io.adserve.targeting.grpc.TargetingServiceGrpc;
import io.adserve.user.grpc.GetUserRequest;
import io.adserve.user.grpc.GetUserResponse;
import io.adserve.user.grpc.UserServiceGrpc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

@Slf4j
@RestController
@RequestMapping("/api/v1/ads")
public class AdController {

    private final UserServiceGrpc.UserServiceBlockingStub userServiceStub;
    private final SegmentServiceGrpc.SegmentServiceBlockingStub segmentServiceStub;
    private final TargetingServiceGrpc.TargetingServiceBlockingStub targetingServiceStub;
    private final MlInferenceClient mlInferenceClient;
    private final PartnerClientRegistry partnerClientRegistry;
    private final BusinessMetrics businessMetrics;
    private final BottleneckMetrics bottleneckMetrics;

    public AdController(
            UserServiceGrpc.UserServiceBlockingStub userServiceStub,
            SegmentServiceGrpc.SegmentServiceBlockingStub segmentServiceStub,
            TargetingServiceGrpc.TargetingServiceBlockingStub targetingServiceStub,
            MlInferenceClient mlInferenceClient,
            PartnerClientRegistry partnerClientRegistry,
            BusinessMetrics businessMetrics,
            BottleneckMetrics bottleneckMetrics) {
        this.userServiceStub = userServiceStub;
        this.segmentServiceStub = segmentServiceStub;
        this.targetingServiceStub = targetingServiceStub;
        this.mlInferenceClient = mlInferenceClient;
        this.partnerClientRegistry = partnerClientRegistry;
        this.businessMetrics = businessMetrics;
        this.bottleneckMetrics = bottleneckMetrics;
    }

    @PostMapping("/request")
    public AdResponse requestAd(@RequestBody AdRequest request) {
        var startTime = Instant.now();
        var requestId = UUID.randomUUID().toString();
        var traceId = UUID.randomUUID().toString();
        var userId = request.userId() != null ? request.userId() : "unknown";

        businessMetrics.incrementRequestsTotal();
        bottleneckMetrics.incrementActiveRequests();

        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {

            var internalStart = Instant.now();
            var userTask = scope.fork(() -> callUserService(userId, traceId, requestId));
            var segmentTask = scope.fork(() -> callSegmentService(userId, traceId, requestId));
            var targetingTask = scope.fork(() -> callTargetingService(userId, traceId, requestId));
            scope.join();

            var userResponse = userTask.get();
            var segmentResponse = segmentTask.get();
            var targetingResponse = targetingTask.get();

            var internalDuration = Duration.between(internalStart, Instant.now());
            bottleneckMetrics.recordPhaseInternal(internalDuration);

            var mlStart = Instant.now();
            var mlPrediction = callMlService(userId, traceId, segmentResponse);
            var mlDuration = Duration.between(mlStart, Instant.now());
            bottleneckMetrics.recordPhaseMl(mlDuration);

            var partnerStart = Instant.now();
            var auctionResult = runAuction(requestId, traceId);
            var partnerDuration = Duration.between(partnerStart, Instant.now());
            bottleneckMetrics.recordPhasePartner(partnerDuration);

            businessMetrics.recordAuctionWin(auctionResult.winnerId());

            var processingTime = Duration.between(startTime, Instant.now());
            businessMetrics.recordRequestDuration(processingTime);
            businessMetrics.incrementRequestsSuccess();
            bottleneckMetrics.decrementActiveRequests();

            log.info("Ad request completed | requestId={} | traceId={} | totalMs={} | winner={} | price=${}",
                    requestId, traceId, processingTime.toMillis(),
                    auctionResult.winnerId(), String.format("%.2f", auctionResult.winningPrice()));

            return buildResponse(requestId, traceId, processingTime.toMillis(),
                    userResponse, segmentResponse, targetingResponse, mlPrediction, auctionResult);

        } catch (Exception e) {
            var processingTime = Duration.between(startTime, Instant.now());
            businessMetrics.recordRequestDuration(processingTime);
            businessMetrics.incrementRequestsError();
            bottleneckMetrics.decrementActiveRequests();
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

    private Map<String, Object> callMlService(String userId, String traceId, GetSegmentsResponse segmentResponse) {
        var segmentNames = segmentResponse.getSegmentsList().stream().map(Segment::getName).toList();
        return mlInferenceClient.predict(Map.of("userId", userId, "traceId", traceId, "segments", segmentNames));
    }

    private AuctionResult runAuction(String requestId, String traceId) {
        var bids = new ArrayList<Bid>();
        var partnerIds = partnerClientRegistry.getPartnerIds();

        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<Map<String, Object>>allSuccessfulOrThrow())) {

            var tasks = new HashMap<String, Subtask<Map<String, Object>>>();
            for (var partnerId : partnerIds) {
                tasks.put(partnerId, scope.fork(() ->
                    partnerClientRegistry.get(partnerId).bid(Map.of("id", requestId, "traceId", traceId))));
            }

            try { scope.join(); } catch (Exception e) { /* timeout - continue with partial results */ }

            for (var entry : tasks.entrySet()) {
                if (entry.getValue().state() == Subtask.State.SUCCESS) {
                    var bid = extractBid(entry.getKey(), entry.getValue().get());
                    if (bid != null) bids.add(bid);
                }
            }
        } catch (Exception e) {
            log.warn("Partner bidding failed | traceId={} | error={}", traceId, e.getMessage());
        }

        var winner = bids.stream()
                .max(Comparator.comparingDouble(Bid::price))
                .orElse(new Bid("none", 0.0, "", ""));

        return new AuctionResult(winner.partnerId(), winner.price(), winner.adId(),
                winner.creativeUrl(), partnerIds.size(), bids.size());
    }

    @SuppressWarnings("unchecked")
    private Bid extractBid(String partnerId, Map<String, Object> response) {
        try {
            var seatbids = (List<Map<String, Object>>) response.get("seatbid");
            if (seatbids == null || seatbids.isEmpty()) return null;

            var bidList = (List<Map<String, Object>>) seatbids.getFirst().get("bid");
            if (bidList == null || bidList.isEmpty()) return null;

            var bid = bidList.getFirst();
            return new Bid(partnerId,
                    ((Number) bid.get("price")).doubleValue(),
                    (String) bid.get("adid"),
                    (String) bid.getOrDefault("nurl", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private AdResponse buildResponse(
            String requestId, String traceId, long processingTimeMs,
            GetUserResponse userResponse, GetSegmentsResponse segmentResponse,
            GetTargetingRulesResponse targetingResponse, Map<String, Object> mlPrediction,
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
                new AdResponse.Prediction((Double) mlPrediction.get("ctr"),
                        (Double) mlPrediction.get("cvr"), (String) mlPrediction.get("modelVersion")),
                new AdResponse.Metadata(3, true, auction.partnersCalled(), auction.bidsReceived())
        );
    }

    // Request/Response records
    public record AdRequest(String userId, String deviceType, Map<String, Object> context) {}

    public record AdResponse(
            String requestId, String traceId, String status, long processingTimeMs,
            Ad ad, User user, List<SegmentInfo> segments, List<TargetingRule> targetingRules,
            Prediction prediction, Metadata metadata) {

        public record Ad(String partnerId, double bidPrice, String adId, String creativeUrl) {}
        public record User(String userId, String name, String country, String deviceType) {}
        public record SegmentInfo(String id, String name, String type, double score) {}
        public record TargetingRule(String id, String name, int priority, List<String> eligiblePartners) {}
        public record Prediction(double ctr, double cvr, String modelVersion) {}
        public record Metadata(int internalServicesCalled, boolean mlServiceCalled, int partnersCalled, int bidsReceived) {}
    }

    // Internal records
    record Bid(String partnerId, double price, String adId, String creativeUrl) {}
    record AuctionResult(String winnerId, double winningPrice, String adId, String creativeUrl,
                         int partnersCalled, int bidsReceived) {}
}
