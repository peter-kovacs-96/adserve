package io.adserve.orchestration.config;

import io.adserve.orchestration.client.partner.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.util.concurrent.Executors;

@Configuration
public class PartnerClientConfig {

    private final HttpClientProperties properties;

    public PartnerClientConfig(HttpClientProperties properties) {
        this.properties = properties;
    }

    @Bean
    public NikeClient nikeClient() {
        return createClient("nike", NikeClient.class);
    }

    @Bean
    public AdidasClient adidasClient() {
        return createClient("adidas", AdidasClient.class);
    }

    @Bean
    public PumaClient pumaClient() {
        return createClient("puma", PumaClient.class);
    }

    @Bean
    public UnderArmourClient underArmourClient() {
        return createClient("underarmour", UnderArmourClient.class);
    }

    @Bean
    public NewBalanceClient newBalanceClient() {
        return createClient("newbalance", NewBalanceClient.class);
    }

    @Bean
    public AsicsClient asicsClient() {
        return createClient("asics", AsicsClient.class);
    }

    @Bean
    public ReebokClient reebokClient() {
        return createClient("reebok", ReebokClient.class);
    }

    @Bean
    public SkechersClient skechersClient() {
        return createClient("skechers", SkechersClient.class);
    }

    @Bean
    public ColumbiaClient columbiaClient() {
        return createClient("columbia", ColumbiaClient.class);
    }

    @Bean
    public NorthFaceClient northFaceClient() {
        return createClient("northface", NorthFaceClient.class);
    }

    private <T extends PartnerBidClient> T createClient(String partnerId, Class<T> clientType) {
        var config = properties.partners().get(partnerId);

        var httpClient = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(config.readTimeout());

        var restClient = RestClient.builder()
                .baseUrl(config.baseUrl())
                .requestFactory(requestFactory)
                .build();

        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(clientType);
    }
}
