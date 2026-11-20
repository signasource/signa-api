package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.common.MetricsService;
import com.signasource.signa_api.config.R2Properties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignAnimationService {

    private final S3Presigner r2Presigner;
    private final R2Properties r2Properties;
    private final MetricsService metricsService;

    public String presignedGetUrl(String objectKey) {
        long start = System.nanoTime();
        try {
            GetObjectRequest getObjectRequest =
                    GetObjectRequest.builder().bucket(r2Properties.bucket()).key(objectKey).build();

            GetObjectPresignRequest presignRequest =
                    GetObjectPresignRequest.builder()
                            .signatureDuration(
                                    Duration.ofMinutes(r2Properties.presignExpiryMinutes()))
                            .getObjectRequest(getObjectRequest)
                            .build();

            String url = r2Presigner.presignGetObject(presignRequest).url().toString();
            metricsService.recordR2Presign(elapsed(start), true);
            return url;
        } catch (RuntimeException ex) {
            metricsService.recordR2Presign(elapsed(start), false);
            log.error("Failed to presign R2 object key '{}'", objectKey, ex);
            throw ex;
        }
    }

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }
}
