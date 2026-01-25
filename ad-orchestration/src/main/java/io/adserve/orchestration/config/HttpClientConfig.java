package io.adserve.orchestration.config;

import io.adserve.orchestration.client.MlInferenceClient;
import io.adserve.orchestration.client.PartnerClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpClientConfig {

    @Value("${http.client.ml-inference.base-url:http://localhost:8081}")
    private String mlInferenceBaseUrl;

    @Value("${http.client.partner.base-url:http://localhost:8082}")
    private String partnerBaseUrl;

    @Bean
    public MlInferenceClient mlInferenceClient() {
        var restClient = RestClient.builder()
                .baseUrl(mlInferenceBaseUrl)
                .build();

        var adapter = RestClientAdapter.create(restClient);
        var factory = HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(MlInferenceClient.class);
    }

    @Bean
    public PartnerClient partnerClient() {
        var restClient = RestClient.builder()
                .baseUrl(partnerBaseUrl)
                .build();

        var adapter = RestClientAdapter.create(restClient);
        var factory = HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(PartnerClient.class);
    }
}
