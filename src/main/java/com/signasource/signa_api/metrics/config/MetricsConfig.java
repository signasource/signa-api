package com.signasource.signa_api.metrics.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GrafanaProperties.class)
public class MetricsConfig {

    @Bean
    public RestClient grafanaRestClient() {
        return RestClient.create();
    }
}
