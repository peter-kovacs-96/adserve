package io.adserve.orchestration.config;

import io.adserve.orchestration.client.MlInferenceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.util.concurrent.Executors;

@Configuration
public class HttpClientConfig {

    private final HttpClientProperties properties;

    public HttpClientConfig(HttpClientProperties properties) {
        this.properties = properties;
    }

    @Bean
    public MlInferenceClient mlInferenceClient() {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.mlInference().connectTimeout())
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.mlInference().readTimeout());

        var restClient = RestClient.builder()
                .baseUrl(properties.mlInference().baseUrl())
                .requestFactory(requestFactory)
                .build();

        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(MlInferenceClient.class);
    }
}
