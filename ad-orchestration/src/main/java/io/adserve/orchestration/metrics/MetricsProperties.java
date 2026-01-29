package io.adserve.orchestration.metrics;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "metrics")
public class MetricsProperties {

    private boolean businessEnabled = true;
    private boolean bottleneckEnabled = false;

}
