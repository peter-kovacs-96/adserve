package io.adserve.orchestration.config;

import io.adserve.orchestration.client.MlInferenceClient;
import io.adserve.orchestration.client.partner.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import java.net.http.HttpClient;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
@EnableResilientMethods
@EnableConfigurationProperties(HttpClientProperties.class)
@ImportHttpServices(group = "nike", types = {NikeClient.class})
@ImportHttpServices(group = "adidas", types = {AdidasClient.class})
@ImportHttpServices(group = "northface", types = {NorthFaceClient.class})
@ImportHttpServices(group = "mlinference", types = {MlInferenceClient.class})
public class HttpClientConfig {

    @Bean
    RestClientHttpServiceGroupConfigurer httpServiceGroupConfigurer(HttpClientProperties properties) {
        log.info("HTTP Client config: connectTimeout={}, readTimeout={}",
                properties.connectTimeout(), properties.readTimeout());

        // Single shared HttpClient for all groups
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        return groups -> groups.forEachClient((group, builder) -> {
            log.info("Configuring HTTP client for group: {} with baseUrl: {}",
                    group.name(), properties.groups().get(group.name()));

            var factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(properties.readTimeout());
            builder.baseUrl(properties.groups().get(group.name())).requestFactory(factory);
        });
    }
}