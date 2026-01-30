package io.adserve.orchestration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AdOrchestrationApplication {

    static void main(String[] args) {
        SpringApplication.run(AdOrchestrationApplication.class, args);
    }
}
