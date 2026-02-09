package io.adserve.targeting.service;

import io.adserve.targeting.grpc.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TargetingGrpcService extends TargetingServiceGrpc.TargetingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(TargetingGrpcService.class);

    @Override
    public void getTargetingRules(GetTargetingRulesRequest request, StreamObserver<GetTargetingRulesResponse> responseObserver) {
        log.info("GetTargetingRules request received - userId: {}, segmentIds: {}, traceId: {}, requestId: {}",
                request.getUserId(), request.getSegmentIdsList(), request.getTraceId(), request.getRequestId());

        try {
            var sportsRule = TargetingRule.newBuilder()
                    .setId("rule-001")
                    .setName("Sports Apparel Targeting")
                    .setPriority(1)
                    .addCriteria(Criteria.newBuilder()
                            .setField("segment")
                            .setOperator(Operator.CONTAINS)
                            .setValue("sports_fan")
                            .build())
                    .addEligiblePartners("nike")
                    .addEligiblePartners("adidas")
                    .addEligiblePartners("puma")
                    .build();

            var techRule = TargetingRule.newBuilder()
                    .setId("rule-002")
                    .setName("Tech Products Targeting")
                    .setPriority(2)
                    .addCriteria(Criteria.newBuilder()
                            .setField("segment")
                            .setOperator(Operator.CONTAINS)
                            .setValue("tech_enthusiast")
                            .build())
                    .addEligiblePartners("apple")
                    .addEligiblePartners("samsung")
                    .addEligiblePartners("google")
                    .build();

            var premiumRule = TargetingRule.newBuilder()
                    .setId("rule-003")
                    .setName("Premium Brands Targeting")
                    .setPriority(3)
                    .addCriteria(Criteria.newBuilder()
                            .setField("segment")
                            .setOperator(Operator.CONTAINS)
                            .setValue("high_income")
                            .build())
                    .addEligiblePartners("rolex")
                    .addEligiblePartners("bmw")
                    .addEligiblePartners("gucci")
                    .build();

            var response = GetTargetingRulesResponse.newBuilder()
                    .addRules(sportsRule)
                    .addRules(techRule)
                    .addRules(premiumRule)
                    .setTraceId(request.getTraceId())
                    .setRequestId(request.getRequestId())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("GetTargetingRules response sent - userId: {}, ruleCount: {}, traceId: {}",
                    request.getUserId(), response.getRulesCount(), request.getTraceId());

        } catch (Exception e) {
            log.error("Error processing GetTargetingRules request - traceId: {}", request.getTraceId(), e);
            responseObserver.onError(e);
        }
    }
}
