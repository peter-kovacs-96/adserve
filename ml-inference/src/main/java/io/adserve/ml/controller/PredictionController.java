package io.adserve.ml.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/v1")
public class PredictionController {

    private static final Logger log = LoggerFactory.getLogger(PredictionController.class);

    @PostMapping("/predict")
    public Map<String, Object> predict(@RequestBody Map<String, Object> request) {
        var userId = request.getOrDefault("userId", "unknown");
        var traceId = request.getOrDefault("traceId", "");

        log.info("Prediction request received - userId: {}, traceId: {}", userId, traceId);

        // Base values with small random variation
        var random = ThreadLocalRandom.current();
        var ctr = 0.02 + random.nextDouble(0.01, 0.03);
        var cvr = 0.005 + random.nextDouble(0.003, 0.008);

        var response = Map.of(
                "ctr", Math.round(ctr * 1000.0) / 1000.0,
                "cvr", Math.round(cvr * 1000.0) / 1000.0,
                "modelVersion", "v1.0.0",
                "traceId", traceId
        );

        log.info("Prediction response - userId: {}, ctr: {}, cvr: {}, traceId: {}",
                userId, response.get("ctr"), response.get("cvr"), traceId);

        return response;
    }
}
