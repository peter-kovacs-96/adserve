package io.adserve.ml.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/v1")
public class PredictionController {

    private static final Logger log = LoggerFactory.getLogger(PredictionController.class);

    @PostMapping("/predict")
    public PredictionResponse predict(@RequestBody PredictionRequest request) {
        log.info("Prediction request received - userId: {}, traceId: {}", request.userId(), request.traceId());

        // Base values with small random variation
        var random = ThreadLocalRandom.current();
        var ctr = Math.round((0.02 + random.nextDouble(0.01, 0.03)) * 1000.0) / 1000.0;
        var cvr = Math.round((0.005 + random.nextDouble(0.003, 0.008)) * 1000.0) / 1000.0;

        log.info("Prediction response - userId: {}, ctr: {}, cvr: {}, traceId: {}",
                request.userId(), ctr, cvr, request.traceId());

        return new PredictionResponse(ctr, cvr, "v1.0.0", request.traceId());
    }

    public record PredictionRequest(
            String userId,
            String traceId,
            List<String> segments
    ) {}

    public record PredictionResponse(
            double ctr,
            double cvr,
            String modelVersion,
            String traceId
    ) {}
}
