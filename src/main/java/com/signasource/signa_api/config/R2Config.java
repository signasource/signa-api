package com.signasource.signa_api.config;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(R2Properties.class)
public class R2Config {

    @Bean
    public S3Presigner r2Presigner(R2Properties props) {
        S3Presigner.Builder builder =
                S3Presigner.builder()
                        .region(Region.of("auto"))
                        .serviceConfiguration(
                                S3Configuration.builder().pathStyleAccessEnabled(true).build());

        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        if (props.accessKeyId() != null
                && !props.accessKeyId().isBlank()
                && props.secretAccessKey() != null
                && !props.secretAccessKey().isBlank()) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                    props.accessKeyId(), props.secretAccessKey())));
        }
        return builder.build();
    }
}
