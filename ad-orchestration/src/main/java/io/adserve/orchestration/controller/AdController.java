package io.adserve.orchestration.controller;

import io.adserve.orchestration.client.MlInferenceClient;
import io.adserve.orchestration.client.PartnerClient;
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
import org.springframework.beans.factory.annotation.Value;
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

    private static final List<String> PARTNERS = List.of(
            "nike", "adidas", "puma", "underarmour", "newbalance",
            "asics", "reebok", "sketchers", "columbia", "northface"
    );

    private final UserServiceGrpc.UserServiceBlockingStub userServiceStub;
    private final SegmentServiceGrpc.SegmentServiceBlockingStub segmentServiceStub;
    private final TargetingServiceGrpc.TargetingServiceBlockingStub targetingServiceStub;
    private final MlInferenceClient mlInferenceClient;
    private final PartnerClient partnerClient;
    private final long deadlineMs;
    private final long partnerTimeoutMs;

    // Metrics
    private final BusinessMetrics businessMetrics;
    private final BottleneckMetrics bottleneckMetrics;

    public AdController(
            UserServiceGrpc.UserServiceBlockingStub userServiceStub,
            SegmentServiceGrpc.SegmentServiceBlockingStub segmentServiceStub,
            TargetingServiceGrpc.TargetingServiceBlockingStub targetingServiceStub,
            MlInferenceClient mlInferenceClient,
            PartnerClient partnerClient,
            BusinessMetrics businessMetrics,
            BottleneckMetrics bottleneckMetrics,
            @Value("${grpc.client.deadline-ms:10}") long deadlineMs,
            @Value("${http.client.partner.timeout-ms:80}") long partnerTimeoutMs) {
        this.userServiceStub = userServiceStub;
        this.segmentServiceStub = segmentServiceStub;
        this.targetingServiceStub = targetingServiceStub;
        this.mlInferenceClient = mlInferenceClient;
        this.partnerClient = partnerClient;
        this.businessMetrics = businessMetrics;
        this.bottleneckMetrics = bottleneckMetrics;
        this.deadlineMs = deadlineMs;
        this.partnerTimeoutMs = partnerTimeoutMs;
    }

    @PostMapping("/request")
    public Map<String, Object> requestAd(@RequestBody Map<String, Object> request) {
        var startTime = Instant.now();
        var requestId = UUID.randomUUID().toString();
        var traceId = UUID.randomUUID().toString();
        var userId = (String) request.getOrDefault("userId", "unknown");

        businessMetrics.incrementRequestsTotal();
        int activeCount = bottleneckMetrics.incrementActiveRequests();

        log.info("[REQUEST] Ad request received | requestId={} | traceId={} | userId={} | activeRequests={}",
                requestId, traceId, userId, activeCount);

        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow(),
                cf -> cf.withTimeout(Duration.ofMillis(deadlineMs)))) {

            // Fork parallel calls to internal services
            var internalStart = Instant.now();
            log.info("[INTERNAL] Starting parallel gRPC calls | traceId={}", traceId);

            var userTask = scope.fork(() -> callUserService(userId, traceId, requestId));
            var segmentTask = scope.fork(() -> callSegmentService(userId, traceId, requestId));
            var targetingTask = scope.fork(() -> callTargetingService(userId, traceId, requestId));

            // Wait for all tasks to complete
            scope.join();

            // Get results
            var userResponse = userTask.get();
            var segmentResponse = segmentTask.get();
            var targetingResponse = targetingTask.get();

            var internalDuration = Duration.between(internalStart, Instant.now());
            var internalTimeMs = internalDuration.toMillis();
            bottleneckMetrics.recordPhaseInternal(internalDuration);
            log.info("[INTERNAL] Completed | traceId={} | timeMs={} | segments={} | rules={}",
                    traceId, internalTimeMs, segmentResponse.getSegmentsCount(), targetingResponse.getRulesCount());

            // Call ML service (after internal services)
            var mlStart = Instant.now();
            var mlPrediction = callMlService(userId, traceId, segmentResponse);
            var mlDuration = Duration.between(mlStart, Instant.now());
            var mlTimeMs = mlDuration.toMillis();
            bottleneckMetrics.recordPhaseMl(mlDuration);
            bottleneckMetrics.recordHttpMlCall(mlDuration);
            log.info("[ML] Completed | traceId={} | timeMs={} | ctr={} | cvr={}",
                    traceId, mlTimeMs, mlPrediction.get("ctr"), mlPrediction.get("cvr"));

            // Call partners in parallel
            var partnerStart = Instant.now();
            log.info("[PARTNERS] Starting parallel calls to {} partners | traceId={}", PARTNERS.size(), traceId);
            var auctionResult = callPartnersAndRunAuction(requestId, traceId);
            var partnerDuration = Duration.between(partnerStart, Instant.now());
            var partnerTimeMs = partnerDuration.toMillis();
            bottleneckMetrics.recordPhasePartner(partnerDuration);

            log.info("[AUCTION] Winner selected | traceId={} | winner={} | price=${} | bids={}/{}",
                    traceId, auctionResult.winnerId(), String.format("%.2f", auctionResult.winningPrice()),
                    auctionResult.bidsReceived(), auctionResult.partnersCalled());

            // Record winner metric
            businessMetrics.recordAuctionWin(auctionResult.winnerId());

            var processingTimeMs = Duration.between(startTime, Instant.now()).toMillis();
            businessMetrics.recordRequestDuration(Duration.ofMillis(processingTimeMs));
            businessMetrics.incrementRequestsSuccess();
            bottleneckMetrics.decrementActiveRequests();

            log.info("[RESPONSE] Request completed | requestId={} | traceId={} | totalMs={} | internalMs={} | mlMs={} | partnersMs={} | activeRequests={}",
                    requestId, traceId, processingTimeMs, internalTimeMs, mlTimeMs, partnerTimeMs, bottleneckMetrics.getActiveRequests());

            return buildSuccessResponse(requestId, traceId, processingTimeMs,
                    userResponse, segmentResponse, targetingResponse, mlPrediction, auctionResult);

        } catch (Exception e) {
            var processingTimeMs = Duration.between(startTime, Instant.now()).toMillis();
            businessMetrics.recordRequestDuration(Duration.ofMillis(processingTimeMs));
            businessMetrics.incrementRequestsError();
            bottleneckMetrics.decrementActiveRequests();

            log.error("[ERROR] Request failed | requestId={} | traceId={} | timeMs={} | error={} | activeRequests={}",
                    requestId, traceId, processingTimeMs, e.getMessage(), bottleneckMetrics.getActiveRequests(), e);

            return buildErrorResponse(requestId, traceId, processingTimeMs, e.getMessage());
        }
    }

    private GetUserResponse callUserService(String userId, String traceId, String requestId) {
        var start = Instant.now();
        try {
            var request = GetUserRequest.newBuilder()
                    .setUserId(userId)
                    .setTraceId(traceId)
                    .setRequestId(requestId)
                    .build();
            return userServiceStub.getUser(request);
        } finally {
            bottleneckMetrics.recordGrpcUserCall(Duration.between(start, Instant.now()));
        }
    }

    private GetSegmentsResponse callSegmentService(String userId, String traceId, String requestId) {
        var start = Instant.now();
        try {
            var request = GetSegmentsRequest.newBuilder()
                    .setUserId(userId)
                    .setTraceId(traceId)
                    .setRequestId(requestId)
                    .build();
            return segmentServiceStub.getSegments(request);
        } finally {
            bottleneckMetrics.recordGrpcSegmentCall(Duration.between(start, Instant.now()));
        }
    }

    private GetTargetingRulesResponse callTargetingService(String userId, String traceId, String requestId) {
        var start = Instant.now();
        try {
            var request = GetTargetingRulesRequest.newBuilder()
                    .setUserId(userId)
                    .setTraceId(traceId)
                    .setRequestId(requestId)
                    .build();
            return targetingServiceStub.getTargetingRules(request);
        } finally {
            bottleneckMetrics.recordGrpcTargetingCall(Duration.between(start, Instant.now()));
        }
    }

    private Map<String, Object> callMlService(String userId, String traceId, GetSegmentsResponse segmentResponse) {
        var segmentNames = segmentResponse.getSegmentsList().stream()
                .map(Segment::getName)
                .toList();

        var mlRequest = Map.of(
                "userId", userId,
                "traceId", traceId,
                "segments", segmentNames
        );

        return mlInferenceClient.predict(mlRequest);
    }

    private AuctionResult callPartnersAndRunAuction(String requestId, String traceId) {
        var bids = new ArrayList<Bid>();
        var partnersCalled = PARTNERS.size();
        var bidsReceived = 0;

        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<Map<String, Object>>allSuccessfulOrThrow(),
                cf -> cf.withTimeout(Duration.ofMillis(partnerTimeoutMs)))) {

            // Fork tasks for all partners
            var tasks = new HashMap<String, Subtask<Map<String, Object>>>();
            for (var partnerId : PARTNERS) {
                var task = scope.fork(() -> callPartner(partnerId, requestId, traceId));
                tasks.put(partnerId, task);
            }

            // Wait for all (with timeout)
            try {
                scope.join();
            } catch (Exception e) {
                log.warn("[PARTNERS] Timeout or interruption | traceId={} | error={}", traceId, e.getMessage());
            }

            // Collect successful bids
            var successfulPartners = new ArrayList<String>();
            var failedPartners = new ArrayList<String>();

            for (var entry : tasks.entrySet()) {
                var partnerId = entry.getKey();
                var task = entry.getValue();

                if (task.state() == Subtask.State.SUCCESS) {
                    try {
                        var response = task.get();
                        var bid = extractBid(partnerId, response);
                        if (bid != null) {
                            bids.add(bid);
                            bidsReceived++;
                            successfulPartners.add(partnerId + "=$" + String.format("%.2f", bid.price()));
                        }
                    } catch (Exception e) {
                        failedPartners.add(partnerId);
                    }
                } else {
                    failedPartners.add(partnerId);
                }
            }

            log.info("[PARTNERS] Bids collected | traceId={} | successful={} | failed={}",
                    traceId, successfulPartners, failedPartners.isEmpty() ? "none" : failedPartners);

        } catch (Exception e) {
            log.error("[PARTNERS] Scope failed | traceId={} | error={}", traceId, e.getMessage());
        }

        // Run auction: pick highest bid
        var winner = bids.stream()
                .max(Comparator.comparingDouble(Bid::price))
                .orElse(new Bid("none", 0.0, "", ""));

        return new AuctionResult(winner.partnerId(), winner.price(), winner.adId(),
                winner.creativeUrl(), partnersCalled, bidsReceived);
    }

    private Map<String, Object> callPartner(String partnerId, String requestId, String traceId) {
        Map<String, Object> bidRequest = Map.of(
                "id", requestId,
                "traceId", traceId
        );
        return partnerClient.bid(partnerId, bidRequest);
    }

    @SuppressWarnings("unchecked")
    private Bid extractBid(String partnerId, Map<String, Object> response) {
        try {
            var seatbids = (List<Map<String, Object>>) response.get("seatbid");
            if (seatbids == null || seatbids.isEmpty()) {
                return null;
            }

            var seatbid = seatbids.getFirst();
            var bids = (List<Map<String, Object>>) seatbid.get("bid");
            if (bids == null || bids.isEmpty()) {
                return null;
            }

            var bid = bids.getFirst();
            var price = ((Number) bid.get("price")).doubleValue();
            var adId = (String) bid.get("adid");
            var nurl = (String) bid.getOrDefault("nurl", "");

            return new Bid(partnerId, price, adId, nurl);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> buildSuccessResponse(
            String requestId,
            String traceId,
            long processingTimeMs,
            GetUserResponse userResponse,
            GetSegmentsResponse segmentResponse,
            GetTargetingRulesResponse targetingResponse,
            Map<String, Object> mlPrediction,
            AuctionResult auctionResult) {

        var response = new LinkedHashMap<String, Object>();
        response.put("requestId", requestId);
        response.put("traceId", traceId);
        response.put("status", "success");
        response.put("ad", Map.of(
                "partnerId", auctionResult.winnerId(),
                "bidPrice", auctionResult.winningPrice(),
                "adId", auctionResult.adId(),
                "adCreativeUrl", auctionResult.creativeUrl()
        ));
        response.put("processingTimeMs", processingTimeMs);
        response.put("user", Map.of(
                "userId", userResponse.getUser().getUserId(),
                "name", userResponse.getUser().getName(),
                "country", userResponse.getUser().getDemographics().getCountry(),
                "deviceType", userResponse.getUser().getDevice().getDeviceType()
        ));
        response.put("segments", segmentResponse.getSegmentsList().stream()
                .map(s -> Map.of(
                        "id", s.getId(),
                        "name", s.getName(),
                        "type", s.getType().name(),
                        "score", s.getScore()
                ))
                .toList());
        response.put("targetingRules", targetingResponse.getRulesList().stream()
                .map(r -> Map.of(
                        "id", r.getId(),
                        "name", r.getName(),
                        "priority", r.getPriority(),
                        "eligiblePartners", r.getEligiblePartnersList()
                ))
                .toList());
        response.put("prediction", Map.of(
                "ctr", mlPrediction.get("ctr"),
                "cvr", mlPrediction.get("cvr"),
                "modelVersion", mlPrediction.get("modelVersion")
        ));
        response.put("metadata", Map.of(
                "internalServicesCalled", 3,
                "mlServiceCalled", true,
                "partnersCalled", auctionResult.partnersCalled(),
                "bidsReceived", auctionResult.bidsReceived()
        ));

        return response;
    }

    private Map<String, Object> buildErrorResponse(String requestId, String traceId, long processingTimeMs, String error) {
        var response = new LinkedHashMap<String, Object>();
        response.put("requestId", requestId);
        response.put("traceId", traceId);
        response.put("status", "error");
        response.put("error", error);
        response.put("processingTimeMs", processingTimeMs);
        return response;
    }

    record Bid(String partnerId, double price, String adId, String creativeUrl) {}

    record AuctionResult(String winnerId, double winningPrice, String adId, String creativeUrl,
                         int partnersCalled, int bidsReceived) {}
}
