package io.adserve.orchestration.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

@HttpExchange
public interface PartnerClient {

    @PostExchange("/partners/{partnerId}/bid")
    Map<String, Object> bid(@PathVariable String partnerId, @RequestBody Map<String, Object> request);
}
