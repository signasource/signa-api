package com.signasource.signa_api.config;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
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
        applyCredentials(props, builder::credentialsProvider);
        return builder.build();
    }

    /** S3 client used only for the periodic R2 reachability probe (see R2HealthProbe). */
    @Bean
    public S3Client r2Client(R2Properties props) {
        S3ClientBuilder builder =
                S3Client.builder()
                        .region(Region.of("auto"))
                        .serviceConfiguration(
                                S3Configuration.builder().pathStyleAccessEnabled(true).build());

        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        applyCredentials(props, builder::credentialsProvider);
        return builder.build();
    }

    private static void applyCredentials(
            R2Properties props, java.util.function.Consumer<StaticCredentialsProvider> sink) {
        if (props.accessKeyId() != null
                && !props.accessKeyId().isBlank()
                && props.secretAccessKey() != null
                && !props.secretAccessKey().isBlank()) {
            sink.accept(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                    props.accessKeyId(), props.secretAccessKey())));
        }
    }
}
